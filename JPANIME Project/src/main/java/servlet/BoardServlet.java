package servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import common.DBConn;
import common.Json;

/**
 * 【サーブレット名】BoardServlet
 * 【URLマッピング】/api/board
 * 【機能概要】
 *   1. GET  (bnoなし): レビュー一覧 (検索・並び替え・ページネーション・「自分のレビュー」絞り込み)
 *   2. GET  (bno指定): レビュー詳細 (閲覧数を +1)
 *   3. POST: 新規投稿 / 修正(action=update) / 削除(action=delete)
 *
 * 【GET 一覧のクエリパラメータ】
 *   q     : 検索語 (作品名・タイトル・本文の部分一致 / 大文字小文字無視)
 *   sort  : latest(既定) | oldest | rating | hits
 *   page  : ページ番号 (1始まり)
 *   size  : 1ページの件数 (既定9 / 最大30)
 *   mine  : 1 のとき、ログイン中ユーザー自身のレビューのみ
 *
 * 【GET 一覧のレスポンス】 { items:[...], total, page, size, totalPages }
 *   ※ v1 は配列を直接返していたため、フロント(board.html)側も対応済み。
 *
 * 【v2 の主な変更点】
 *   - 検索/並び替え/ページネーションを DB 側(ROWNUM ネスト = Oracle 11g 互換)で実施
 *   - 画像アップロードの検証 (拡張子ホワイトリスト + 先頭バイト(マジックナンバー)検査)
 *   - 修正・削除時の物理ファイル削除で、クライアント送信値ではなく DB 上のファイル名を使用
 *     (従来は existingImage をそのまま信用しており、パストラバーサルで任意ファイル削除が可能だった)
 *   - 閲覧数: 同一セッションでの重複加算を防止、編集画面の読み込み(edit=1)では加算しない
 *   - 入力長・評価値(1〜5)のサーバー側検証
 *   - JSON 生成を Gson に統一
 */
@WebServlet("/api/board")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 1, // 1MB (一時メモリ格納の閾値)
        maxFileSize = 1024 * 1024 * 10,      // 10MB (1ファイルあたりの最大容量)
        maxRequestSize = 1024 * 1024 * 20    // 20MB (1リクエストあたりの最大容量)
)
public class BoardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // ---- 一覧の制限値 ----
    private static final int DEFAULT_PAGE_SIZE = 9;
    private static final int MAX_PAGE_SIZE = 30;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int PREVIEW_LENGTH = 160; // 一覧カードに載せる本文の最大文字数

    // ---- 入力長の上限 (DB列は文字数セマンティクス。日本語=3byteでも収まる範囲に設定) ----
    private static final int MAX_ANIME_TITLE = 100;
    private static final int MAX_TITLE = 100;
    private static final int MAX_CONTENT = 1000;

    // ---- アップロード画像の許可拡張子と保存名の形式 ----
    private static final Set<String> ALLOWED_EXT =
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
    private static final Pattern STORED_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|gif|webp)$");

    private static final String VIEWED_ATTR = "viewedBnos";

    // =====================================================================
    //  GET
    // =====================================================================

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String bnoParam = request.getParameter("bno");
        if (bnoParam != null && !bnoParam.trim().isEmpty()) {
            try {
                getSingleReview(request, response, Integer.parseInt(bnoParam.trim()));
            } catch (NumberFormatException e) {
                response.getWriter().write("{}");
            }
            return;
        }
        getReviewList(request, response);
    }

    /** 一覧取得 (検索・並び替え・ページネーション) */
    private void getReviewList(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String q = request.getParameter("q") == null ? "" : request.getParameter("q").trim();
        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        String sort = request.getParameter("sort");
        int size = clamp(parseIntOr(request.getParameter("size"), DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE);
        int page = Math.max(1, parseIntOr(request.getParameter("page"), 1));

        boolean mine = "1".equals(request.getParameter("mine"));
        HttpSession session = request.getSession(false);
        String loginId = (session != null) ? (String) session.getAttribute("loginId") : null;

        // 「自分のレビュー」指定だが未ログイン → 空の結果を返す
        if (mine && loginId == null) {
            Json.write(response, buildListResponse(new ArrayList<Map<String, Object>>(), 0, 1, size));
            return;
        }

        // ---- WHERE句の組み立て (値は全て PreparedStatement のバインド変数) ----
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!q.isEmpty()) {
            where.append(" AND (LOWER(a.ANIME_TITLE) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(a.TITLE) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(a.CONTENT) LIKE ? ESCAPE '\\')");
            String like = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (mine) {
            where.append(" AND a.WRITER = ?");
            params.add(loginId);
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                Json.write(response, buildListResponse(new ArrayList<Map<String, Object>>(), 0, 1, size));
                return;
            }

            // ---- 総件数 ----
            pstmt = conn.prepareStatement("SELECT COUNT(*) FROM ANIME_REVIEWS a" + where);
            bind(pstmt, params, 0);
            rs = pstmt.executeQuery();
            int total = rs.next() ? rs.getInt(1) : 0;
            DBConn.close(rs, pstmt);

            int totalPages = Math.max(1, (int) Math.ceil(total / (double) size));
            page = Math.min(page, totalPages);
            int offset = (page - 1) * size;

            // ---- 該当ページの取得 (Oracle 11g 互換の ROWNUM ネスト) ----
            String sql =
                    "SELECT * FROM ( " +
                    "  SELECT t.*, ROWNUM AS RN FROM ( " +
                    "    SELECT a.BNO, a.ANIME_TITLE, a.TITLE, a.CONTENT, a.RATING, a.IMAGE_FILE, a.HIT_COUNT, a.WRITER, " +
                    "           TO_CHAR(a.REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE_STR, " +
                    "           (SELECT COUNT(*) FROM REVIEW_COMMENTS c WHERE c.BNO = a.BNO) AS COMMENT_CNT " +
                    "    FROM ANIME_REVIEWS a" + where +
                    "    ORDER BY " + orderBy(sort) +
                    "  ) t WHERE ROWNUM <= ? " +
                    ") WHERE RN > ?";
            pstmt = conn.prepareStatement(sql);
            int idx = bind(pstmt, params, 0);
            pstmt.setInt(idx + 1, offset + size);
            pstmt.setInt(idx + 2, offset);
            rs = pstmt.executeQuery();

            List<Map<String, Object>> items = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("bno", rs.getInt("BNO"));
                m.put("animeTitle", nvl(rs.getString("ANIME_TITLE")));
                m.put("title", nvl(rs.getString("TITLE")));
                m.put("content", preview(safeGetContent(rs)));
                m.put("rating", rs.getInt("RATING"));
                m.put("imageFile", nvl(rs.getString("IMAGE_FILE")));
                m.put("hitCount", rs.getInt("HIT_COUNT"));
                m.put("commentCount", rs.getInt("COMMENT_CNT"));
                m.put("writer", nvl(rs.getString("WRITER")));
                m.put("regDate", nvl(rs.getString("REG_DATE_STR")));
                items.add(m);
            }
            Json.write(response, buildListResponse(items, total, page, size));

        } catch (Exception e) {
            e.printStackTrace();
            Json.write(response, buildListResponse(new ArrayList<Map<String, Object>>(), 0, 1, size));
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    private Map<String, Object> buildListResponse(List<Map<String, Object>> items, int total, int page, int size) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", items);
        res.put("total", total);
        res.put("page", page);
        res.put("size", size);
        res.put("totalPages", Math.max(1, (int) Math.ceil(total / (double) size)));
        return res;
    }

    /** ORDER BY 句はホワイトリストから選ぶ (ユーザー入力を SQL に連結しない)。 */
    private String orderBy(String sort) {
        if ("oldest".equals(sort)) return "a.BNO ASC";
        if ("rating".equals(sort)) return "a.RATING DESC, a.BNO DESC";
        if ("hits".equals(sort)) return "NVL(a.HIT_COUNT, 0) DESC, a.BNO DESC";
        return "a.BNO DESC"; // latest
    }

    /** 詳細取得。閲覧数は「同一セッションで初回」かつ「編集画面ではない」場合のみ +1。 */
    private void getSingleReview(HttpServletRequest request, HttpServletResponse response, int bno)
            throws IOException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                response.getWriter().write("{}");
                return;
            }

            if (!"1".equals(request.getParameter("edit")) && markViewed(request, bno)) {
                pstmt = conn.prepareStatement("UPDATE ANIME_REVIEWS SET HIT_COUNT = NVL(HIT_COUNT, 0) + 1 WHERE BNO = ?");
                pstmt.setInt(1, bno);
                pstmt.executeUpdate();
                DBConn.close(pstmt);
            }

            pstmt = conn.prepareStatement(
                    "SELECT BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, " +
                    "TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE_STR " +
                    "FROM ANIME_REVIEWS WHERE BNO = ?");
            pstmt.setInt(1, bno);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("bno", rs.getInt("BNO"));
                m.put("animeTitle", nvl(rs.getString("ANIME_TITLE")));
                m.put("title", nvl(rs.getString("TITLE")));
                m.put("content", safeGetContent(rs));
                m.put("rating", rs.getInt("RATING"));
                m.put("imageFile", nvl(rs.getString("IMAGE_FILE")));
                m.put("hitCount", rs.getInt("HIT_COUNT"));
                m.put("writer", nvl(rs.getString("WRITER")));
                m.put("regDate", nvl(rs.getString("REG_DATE_STR")));
                Json.write(response, m);
            } else {
                response.getWriter().write("{}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.getWriter().write("{}");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    /** このセッションで bno を初めて閲覧するなら true (以後は false)。 */
    @SuppressWarnings("unchecked")
    private boolean markViewed(HttpServletRequest request, int bno) {
        HttpSession session = request.getSession(true);
        synchronized (session) {
            Set<Integer> viewed = (Set<Integer>) session.getAttribute(VIEWED_ATTR);
            if (viewed == null) {
                viewed = new HashSet<>();
                session.setAttribute(VIEWED_ATTR, viewed);
            }
            return viewed.add(bno);
        }
    }

    // =====================================================================
    //  POST
    // =====================================================================

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        String writer = (session != null) ? (String) session.getAttribute("loginId") : null;
        if (writer == null || writer.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/login.html");
            return;
        }

        String action = request.getParameter("action");
        if ("delete".equals(action)) {
            deleteReview(request, response, writer);
        } else if ("update".equals(action)) {
            updateReview(request, response, writer);
        } else {
            insertReview(request, response, writer);
        }
    }

    /** 削除: 作成者本人のレビューのみ。DB削除に成功したら画像ファイルも削除。 */
    private void deleteReview(HttpServletRequest request, HttpServletResponse response, String writer)
            throws IOException {
        int bno;
        try {
            bno = Integer.parseInt(request.getParameter("bno"));
        } catch (Exception e) {
            response.sendRedirect(request.getContextPath() + "/board.html?error=invalid_bno");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                response.sendRedirect(request.getContextPath() + "/board.html?error=delete_fail");
                return;
            }

            String imageFileToDelete = null;
            pstmt = conn.prepareStatement("SELECT IMAGE_FILE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?");
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                imageFileToDelete = rs.getString("IMAGE_FILE");
            }
            DBConn.close(rs, pstmt);

            pstmt = conn.prepareStatement("DELETE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?");
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                deletePhysicalFile(request, imageFileToDelete);
            }
            response.sendRedirect(request.getContextPath() + "/board.html");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/board.html?error=delete_fail");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    /** 新規登録 */
    private void insertReview(HttpServletRequest request, HttpServletResponse response, String writer)
            throws IOException {
        String animeTitle = trimToEmpty(request.getParameter("animeTitle"));
        String title = trimToEmpty(request.getParameter("title"));
        String content = trimToEmpty(request.getParameter("content"));
        int rating = parseRating(request.getParameter("rating"));

        if (animeTitle.isEmpty()) animeTitle = "Untitled";
        if (title.isEmpty()) title = "No Title";
        if (content.isEmpty() || animeTitle.length() > MAX_ANIME_TITLE
                || title.length() > MAX_TITLE || content.length() > MAX_CONTENT) {
            response.sendRedirect(request.getContextPath() + "/board.html?error=invalid_input");
            return;
        }

        String savedFileName;
        try {
            savedFileName = saveUploadedFile(request);
        } catch (IllegalArgumentException e) {
            response.sendRedirect(request.getContextPath() + "/board.html?error=invalid_image");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                deletePhysicalFile(request, savedFileName);
                response.sendRedirect(request.getContextPath() + "/board.html?error=save_db");
                return;
            }

            pstmt = conn.prepareStatement(
                    "INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, REG_DATE) " +
                    "VALUES (SEQ_REVIEW_BNO.NEXTVAL, ?, ?, ?, ?, ?, 0, ?, SYSDATE)");
            pstmt.setString(1, animeTitle);
            pstmt.setString(2, title);
            pstmt.setString(3, content);
            pstmt.setInt(4, rating);
            pstmt.setString(5, savedFileName != null ? savedFileName : "");
            pstmt.setString(6, writer);
            pstmt.executeUpdate();

            response.sendRedirect(request.getContextPath() + "/board.html");
        } catch (Exception e) {
            e.printStackTrace();
            deletePhysicalFile(request, savedFileName); // DB登録に失敗したら孤立ファイルを残さない
            response.sendRedirect(request.getContextPath() + "/board.html?error=save_db");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    /**
     * 修正
     *   1. 対象が本人のレビューか、現在の画像名を DB から取得 (クライアント送信の existingImage は使わない)
     *   2. 新画像があれば検証・保存
     *   3. UPDATE 成功後に、置き換えられた古い画像ファイルを削除
     */
    private void updateReview(HttpServletRequest request, HttpServletResponse response, String writer)
            throws IOException {
        int bno;
        try {
            bno = Integer.parseInt(request.getParameter("bno"));
        } catch (Exception e) {
            response.sendRedirect(request.getContextPath() + "/board.html?error=invalid_bno");
            return;
        }

        String animeTitle = trimToEmpty(request.getParameter("animeTitle"));
        String title = trimToEmpty(request.getParameter("title"));
        String content = trimToEmpty(request.getParameter("content"));
        int rating = parseRating(request.getParameter("rating"));

        if (animeTitle.isEmpty()) animeTitle = "Untitled";
        if (title.isEmpty()) title = "No Title";
        if (content.isEmpty() || animeTitle.length() > MAX_ANIME_TITLE
                || title.length() > MAX_TITLE || content.length() > MAX_CONTENT) {
            response.sendRedirect(request.getContextPath() + "/detail.html?bno=" + bno + "&error=invalid_input");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String newFile = null;

        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                response.sendRedirect(request.getContextPath() + "/board.html?error=update_db");
                return;
            }

            // [1] 所有者確認 + 現在の画像名の取得
            pstmt = conn.prepareStatement("SELECT IMAGE_FILE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?");
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                response.sendRedirect(request.getContextPath() + "/board.html?error=forbidden");
                return;
            }
            String oldImage = nvl(rs.getString("IMAGE_FILE"));
            DBConn.close(rs, pstmt);

            // [2] 新画像の検証・保存
            try {
                newFile = saveUploadedFile(request);
            } catch (IllegalArgumentException e) {
                response.sendRedirect(request.getContextPath() + "/detail.html?bno=" + bno + "&error=invalid_image");
                return;
            }
            String finalImage = (newFile != null) ? newFile : oldImage;

            // [3] 更新
            pstmt = conn.prepareStatement(
                    "UPDATE ANIME_REVIEWS SET ANIME_TITLE = ?, TITLE = ?, CONTENT = ?, RATING = ?, IMAGE_FILE = ? " +
                    "WHERE BNO = ? AND WRITER = ?");
            pstmt.setString(1, animeTitle);
            pstmt.setString(2, title);
            pstmt.setString(3, content);
            pstmt.setInt(4, rating);
            pstmt.setString(5, finalImage);
            pstmt.setInt(6, bno);
            pstmt.setString(7, writer);
            int affected = pstmt.executeUpdate();

            // [4] 更新に成功し、画像が差し替わった場合のみ古いファイルを削除
            if (affected > 0 && newFile != null) {
                deletePhysicalFile(request, oldImage);
            }
            response.sendRedirect(request.getContextPath() + "/detail.html?bno=" + bno);
        } catch (Exception e) {
            e.printStackTrace();
            deletePhysicalFile(request, newFile); // 失敗時は保存済みの新ファイルを取り消す
            response.sendRedirect(request.getContextPath() + "/board.html?error=update_db");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    // =====================================================================
    //  画像アップロード
    // =====================================================================

    /**
     * アップロード画像を検証して保存します。
     *
     * @return 保存したファイル名 (UUID.拡張子)。画像が添付されていなければ null
     * @throws IllegalArgumentException 許可されない拡張子・Content-Type・ファイル内容だった場合
     */
    private String saveUploadedFile(HttpServletRequest request) {
        try {
            Part filePart = request.getPart("animeImage");
            if (filePart == null || filePart.getSize() <= 0) {
                return null;
            }
            String submittedName = extractFileName(filePart);
            if (submittedName == null || submittedName.trim().isEmpty()) {
                return null;
            }

            // (1) 拡張子ホワイトリスト
            int dot = submittedName.lastIndexOf('.');
            String ext = (dot >= 0) ? submittedName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            if (!ALLOWED_EXT.contains(ext)) {
                throw new IllegalArgumentException("invalid extension");
            }
            // (2) Content-Type
            String contentType = filePart.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IllegalArgumentException("invalid content type");
            }
            // (3) 先頭バイトの検査 (拡張子を偽装した非画像ファイルを弾く)
            if (!looksLikeImage(filePart)) {
                throw new IllegalArgumentException("invalid image content");
            }

            String savedFileName = UUID.randomUUID().toString() + "." + ext;
            File targetFile = new File(getUploadDirectory(request), savedFileName);
            filePart.write(targetFile.getAbsolutePath());
            return savedFileName;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[AniLog] 画像保存エラー: " + e.getMessage());
            return null;
        }
    }

    /** JPEG / PNG / GIF / WEBP のマジックナンバーを確認。 */
    private boolean looksLikeImage(Part part) throws IOException {
        byte[] h = new byte[12];
        int n = 0;
        try (InputStream in = part.getInputStream()) {
            int r;
            while (n < h.length && (r = in.read(h, n, h.length - n)) > 0) {
                n += r;
            }
        }
        if (n < 12) return false;
        boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        boolean gif = h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8';
        boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        return jpeg || png || gif || webp;
    }

    /** Servlet 3.0 互換の content-disposition パーサー (Tomcat 7 は getSubmittedFileName 未対応)。 */
    private String extractFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        if (contentDisp == null) return null;
        for (String token : contentDisp.split(";")) {
            if (token.trim().startsWith("filename")) {
                String fileName = token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
                int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
                return (slash >= 0) ? fileName.substring(slash + 1) : fileName;
            }
        }
        return null;
    }

    /**
     * 物理ファイル削除。
     * 渡されたファイル名が「このアプリが生成した UUID.拡張子」の形式でなければ何もしない
     * (../ を含むパス等による任意ファイル削除の防止)。
     */
    private void deletePhysicalFile(HttpServletRequest request, String fileName) {
        if (fileName == null || !STORED_NAME.matcher(fileName).matches()) return;
        try {
            File file = new File(getUploadDirectory(request), fileName);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * アップロードディレクトリの決定
     *   0. 環境変数 JPANIME_UPLOAD_DIR (指定時は最優先。本番はこちらを推奨)
     *   1. 開発環境のソースフォルダ (src/main/webapp/uploads)
     *   2. 実行時Webアプリケーションパス (getRealPath)
     *   3. OSの一時ディレクトリ
     */
    private File getUploadDirectory(HttpServletRequest request) {
        File dir;
        String envDir = System.getenv("JPANIME_UPLOAD_DIR");
        if (envDir != null && !envDir.trim().isEmpty()) {
            dir = new File(envDir.trim());
        } else {
            String realPath = request.getServletContext().getRealPath("/uploads");
            String appRoot = System.getProperty("user.dir");
            File devDir = new File(appRoot, "src" + File.separator + "main" + File.separator
                    + "webapp" + File.separator + "uploads");
            if (devDir.exists() || devDir.getParentFile().exists()) {
                dir = devDir;
            } else if (realPath != null) {
                dir = new File(realPath);
            } else {
                dir = new File(System.getProperty("java.io.tmpdir"), "jpanime_uploads");
            }
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    // =====================================================================
    //  ヘルパー
    // =====================================================================

    /** バインド変数を index 1 から順に設定し、最後に設定した位置(=個数)を返す。 */
    private int bind(PreparedStatement pstmt, List<Object> params, int startIndex) throws Exception {
        int i = startIndex;
        for (Object p : params) {
            i++;
            pstmt.setObject(i, p);
        }
        return i;
    }

    /** LIKE のメタ文字(% _ \)をエスケープ (ESCAPE '\' と対で使用)。 */
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 一覧カード用に本文を先頭 PREVIEW_LENGTH 文字へ切り詰める (サロゲートペアを分断しない)。 */
    private String preview(String content) {
        if (content == null) return "";
        if (content.length() <= PREVIEW_LENGTH) return content;
        int end = PREVIEW_LENGTH;
        if (Character.isHighSurrogate(content.charAt(end - 1))) end--;
        return content.substring(0, end) + "…";
    }

    /** VARCHAR2 / CLOB 両対応の本文抽出 */
    private String safeGetContent(ResultSet rs) {
        try {
            Object obj = rs.getObject("CONTENT");
            if (obj == null) return "";
            if (obj instanceof Clob) return readClob((Clob) obj);
            return rs.getString("CONTENT");
        } catch (Exception e) {
            return "";
        }
    }

    private String readClob(Clob clob) {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = clob.getCharacterStream(); BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception ignored) {
        }
        return sb.toString().trim();
    }

    /** 評価値を 1〜5 に正規化 (不正値は既定の5)。 */
    private int parseRating(String s) {
        int r = parseIntOr(s, 5);
        return (r >= 1 && r <= 5) ? r : 5;
    }

    private static int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
