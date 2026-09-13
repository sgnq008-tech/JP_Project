package servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Paths;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import common.DBConn;

/**
 * 【サーブレット名】BoardServlet
 * 【URLマッピング】/api/board
 * 【機能概要】
 *   1. GET: アニメレビュー一覧取得 (bnoなし) または 単一レビュー詳細取得 (bno指定時)[cite: 1]
 *   2. POST: アニメレビューの新規投稿、修正(action=update)、削除(action=delete)[cite: 1]
 *   3. ファイルアップロード制御 (MultipartConfigによる最大20MBまでの画像受信)
 *   4. 単一レビュー照会時の閲覧数(HIT_COUNT)自動インクリメント(+1)[cite: 1]
 *   5. CLOB/VARCHAR2両対応の安全なテキスト抽出[cite: 1]
 */
@WebServlet("/api/board")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 1, // 1MB (一時メモリ格納の閾値)
        maxFileSize = 1024 * 1024 * 10,      // 10MB (1ファイルあたりの最大容量)
        maxRequestSize = 1024 * 1024 * 20    // 20MB (1リクエストあたりの最大容量)
)
public class BoardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * レビュー取得処理 (GETリクエスト)
     * 【分岐】
     *   - ?bno=X あり: getSingleReview() を呼び出し、特定レビューの詳細JSONを返却[cite: 1]
     *   - ?bno=X なし: 全レビューの一覧JSON配列を返却[cite: 1]
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // レスポンスのMIMEタイプと文字コードをUTF-8 JSONに設定[cite: 1]
        response.setContentType("application/json; charset=UTF-8");
        // ブラウザによる古いキャッシュ保持を防止[cite: 1]
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        // [1] クエリパラメータ bno の存在確認[cite: 1]
        String bnoParam = request.getParameter("bno");
        if (bnoParam != null && !bnoParam.trim().isEmpty()) {
            try {
                // bnoが存在する場合は単一レビュー詳細取得へ委譲[cite: 1]
                getSingleReview(request, response, Integer.parseInt(bnoParam.trim()));
            } catch (NumberFormatException e) {
                // 不正な数値の場合は空JSONを返却[cite: 1]
                response.getWriter().write("{}");
            }
            return;
        }

        // [2] bnoがない場合は全件一覧を取得[cite: 1]
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        StringBuilder json = new StringBuilder("[");

        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                response.getWriter().write("[]");
                return;
            }

            // 閲覧数(HIT_COUNT)を含み、最新登録順(BNO DESC)で抽出[cite: 1]
            String sql = "SELECT BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, " +
                    "TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE " +
                    "FROM ANIME_REVIEWS ORDER BY BNO DESC";
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();

            boolean isFirst = true;
            // 取得結果を1件ずつJSONオブジェクトに組み立て[cite: 1]
            while (rs.next()) {
                if (!isFirst) json.append(",");

                // 本文を安全にStringへ変換 (VARCHAR2 / CLOB 両対応)[cite: 1]
                String contentStr = safeGetContent(rs);

                json.append("{")
                        .append("\"bno\":").append(rs.getInt("BNO")).append(",")
                        .append("\"animeTitle\":\"").append(escapeJson(rs.getString("ANIME_TITLE"))).append("\",")
                        .append("\"title\":\"").append(escapeJson(rs.getString("TITLE"))).append("\",")
                        .append("\"content\":\"").append(escapeJson(contentStr)).append("\",")
                        .append("\"rating\":").append(rs.getInt("RATING")).append(",")
                        .append("\"imageFile\":\"").append(escapeJson(rs.getString("IMAGE_FILE"))).append("\",")
                        .append("\"hitCount\":").append(rs.getInt("HIT_COUNT")).append(",")
                        .append("\"writer\":\"").append(escapeJson(rs.getString("WRITER"))).append("\",")
                        .append("\"regDate\":\"").append(rs.getString("REG_DATE")).append("\"")
                        .append("}");
                isFirst = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // DBリソースの安全な解放[cite: 1]
            DBConn.close(rs, pstmt, conn);
        }
        json.append("]");
        // JSON配列をクライアントへ出力[cite: 1]
        response.getWriter().write(json.toString());
    }

    /**
     * 単一レビュー詳細取得 (GETリクエスト補助メソッド)
     * 【処理フロー】
     *   1. 該当レビューの閲覧数(HIT_COUNT)を +1 アップデート[cite: 1]
     *   2. 該当レビューの全カラムを取得して単一JSONオブジェクトとして返却[cite: 1]
     */
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

            // [ステップ1] 閲覧数(HIT_COUNT)のインクリメント処理[cite: 1]
            String hitSql = "UPDATE ANIME_REVIEWS SET HIT_COUNT = NVL(HIT_COUNT, 0) + 1 WHERE BNO = ?";
            pstmt = conn.prepareStatement(hitSql);
            pstmt.setInt(1, bno);
            pstmt.executeUpdate();
            pstmt.close();

            // [ステップ2] 詳細データの照会[cite: 1]
            String sql = "SELECT BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, " +
                    "TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE " +
                    "FROM ANIME_REVIEWS WHERE BNO = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, bno);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                String contentStr = safeGetContent(rs);
                String json = String.format(
                        "{\"bno\":%d,\"animeTitle\":\"%s\",\"title\":\"%s\",\"content\":\"%s\",\"rating\":%d,\"imageFile\":\"%s\",\"hitCount\":%d,\"writer\":\"%s\",\"regDate\":\"%s\"}",
                        rs.getInt("BNO"),
                        escapeJson(rs.getString("ANIME_TITLE")),
                        escapeJson(rs.getString("TITLE")),
                        escapeJson(contentStr),
                        rs.getInt("RATING"),
                        escapeJson(rs.getString("IMAGE_FILE")),
                        rs.getInt("HIT_COUNT"),
                        escapeJson(rs.getString("WRITER")),
                        rs.getString("REG_DATE")
                );
                response.getWriter().write(json);
            } else {
                // 該当レコードが存在しない場合[cite: 1]
                response.getWriter().write("{}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.getWriter().write("{}");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    /**
     * レビュー登録・修正・削除処理 (POSTリクエスト)
     * 【認証チェック】ログインセッションの存在を確認し、未ログイン時はlogin.htmlへリダイレクト[cite: 1]
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        // [1] セッションからログインユーザーIDを取得[cite: 1]
        HttpSession session = request.getSession(false);
        String writer = (session != null) ? (String) session.getAttribute("loginId") : null;

        // 未ログインの場合はログイン画面へ誘導[cite: 1]
        if (writer == null || writer.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/login.html");
            return;
        }

        // [2] actionパラメータによる処理の分岐[cite: 1]
        String action = request.getParameter("action");
        if ("delete".equals(action)) {
            // レビュー削除[cite: 1]
            deleteReview(request, response, writer);
            return;
        } else if ("update".equals(action)) {
            // レビュー修正[cite: 1]
            updateReview(request, response, writer);
            return;
        }

        // 指定がない場合は新規投稿[cite: 1]
        insertReview(request, response, writer);
    }

    /**
     * レビュー削除処理
     * 【セキュリティ】作成者本人(WRITER = writer)のレビューのみ削除可能[cite: 1]
     * 【物理削除】DBレコード削除後、サーバー上にアップロードされていた実体画像ファイルも同時に削除[cite: 1]
     */
    private void deleteReview(HttpServletRequest request, HttpServletResponse response, String writer)
            throws IOException {
        int bno = 0;
        try {
            bno = Integer.parseInt(request.getParameter("bno"));
        } catch (Exception e) {
            response.sendRedirect(request.getContextPath() + "/board.html?error=invalid_bno");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String imageFileToDelete = null;

        try {
            conn = DBConn.getConnection();
            // 削除前に物理画像ファイル名を照会[cite: 1]
            String findSql = "SELECT IMAGE_FILE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?";
            pstmt = conn.prepareStatement(findSql);
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                imageFileToDelete = rs.getString("IMAGE_FILE");
            }
            pstmt.close();

            // DBレコードの削除[cite: 1]
            String deleteSql = "DELETE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?";
            pstmt = conn.prepareStatement(deleteSql);
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            int affected = pstmt.executeUpdate();

            // DB削除成功かつ画像ファイルが存在する場合、物理ファイルを削除[cite: 1]
            if (affected > 0 && imageFileToDelete != null && !imageFileToDelete.trim().isEmpty()) {
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

    /**
     * レビュー新規登録処理
     * 【機能】入力フォームデータを受け取り、画像保存後にANIME_REVIEWSテーブルへINSERT[cite: 1]
     */
    private void insertReview(HttpServletRequest request, HttpServletResponse response, String writer)
            throws IOException {
        String animeTitle = request.getParameter("animeTitle");
        String title = request.getParameter("title");
        String content = request.getParameter("content");
        int rating = parseRating(request.getParameter("rating"));

        // アップロード画像を保存し、発番されたUUIDファイル名を取得[cite: 1]
        String savedFileName = saveUploadedFile(request);

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
            conn.setAutoCommit(true); // 自動コミットを保証

            // 閲覧数は初期値0として登録
            String sql = "INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, REG_DATE) " +
                    "VALUES (SEQ_REVIEW_BNO.NEXTVAL, ?, ?, ?, ?, ?, 0, ?, SYSDATE)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, (animeTitle != null && !animeTitle.trim().isEmpty()) ? animeTitle.trim() : "Untitled");
            pstmt.setString(2, (title != null && !title.trim().isEmpty()) ? title.trim() : "No Title");
            pstmt.setString(3, content != null ? content : "");
            pstmt.setInt(4, rating);
            pstmt.setString(5, savedFileName != null ? savedFileName : "");
            pstmt.setString(6, writer);
            pstmt.executeUpdate();

            response.sendRedirect(request.getContextPath() + "/board.html");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/board.html?error=save_db");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    /**
     * レビュー修正処理
     * 【画像更新ルール】新画像がアップロードされた場合、既存の古い画像ファイルは物理削除[cite: 1]
     */
    private void updateReview(HttpServletRequest request, HttpServletResponse response, String writer)
            throws IOException {
        int bno = 0;
        try {
            bno = Integer.parseInt(request.getParameter("bno"));
        } catch (Exception e) {
            response.sendRedirect(request.getContextPath() + "/board.html?error=invalid_bno");
            return;
        }

        String animeTitle = request.getParameter("animeTitle");
        String title = request.getParameter("title");
        String content = request.getParameter("content");
        int rating = parseRating(request.getParameter("rating"));
        String existingImage = request.getParameter("existingImage");

        // 新規ファイルアップロードの有無を確認[cite: 1]
        String newUploadedFile = saveUploadedFile(request);
        String finalImage = (newUploadedFile != null) ? newUploadedFile : (existingImage != null ? existingImage : "");

        // 新しい画像が登録された場合、以前の古い画像を物理削除[cite: 1]
        if (newUploadedFile != null && existingImage != null && !existingImage.trim().isEmpty()) {
            deletePhysicalFile(request, existingImage);
        }

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
            conn.setAutoCommit(true);

            String sql = "UPDATE ANIME_REVIEWS " +
                    "SET ANIME_TITLE = ?, TITLE = ?, CONTENT = ?, RATING = ?, IMAGE_FILE = ? " +
                    "WHERE BNO = ? AND WRITER = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, (animeTitle != null && !animeTitle.trim().isEmpty()) ? animeTitle.trim() : "Untitled");
            pstmt.setString(2, (title != null && !title.trim().isEmpty()) ? title.trim() : "No Title");
            pstmt.setString(3, content != null ? content : "");
            pstmt.setInt(4, rating);
            pstmt.setString(5, finalImage);
            pstmt.setInt(6, bno);
            pstmt.setString(7, writer);

            pstmt.executeUpdate();
            // 修正完了後は詳細画面(detail.html?bno=X)へ遷移
            response.sendRedirect(request.getContextPath() + "/detail.html?bno=" + bno);
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/board.html?error=update_db");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    /**
     * VARCHAR2およびCLOB両対応の本文安全抽出メソッド
     * 【機能】DBの型定義がVARCHAR2でもCLOBでも、例外を発生させずに文字列として取得
     */
    private String safeGetContent(ResultSet rs) {
        try {
            Object obj = rs.getObject("CONTENT");
            if (obj == null) return "";
            if (obj instanceof Clob) {
                return readClob((Clob) obj);
            }
            return rs.getString("CONTENT");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * ファイルアップロード保存共通ヘルパー (Tomcat 7 / Servlet 3.0 互換)
     * 【機能】Partからファイルを抽出し、UUIDを付与して保存ディレクトリに書き込み[cite: 1]
     */
    private String saveUploadedFile(HttpServletRequest request) {
        try {
            Part filePart = request.getPart("animeImage");
            if (filePart != null && filePart.getSize() > 0) {
                String submittedName = extractFileName(filePart);
                if (submittedName != null && !submittedName.trim().isEmpty()) {
                    String fileNameOnly = Paths.get(submittedName).getFileName().toString();
                    String ext = "";
                    int dotIdx = fileNameOnly.lastIndexOf(".");
                    if (dotIdx >= 0) {
                        ext = fileNameOnly.substring(dotIdx).toLowerCase();
                    }
                    // ファイル名重複防止のためUUIDでリネーム[cite: 1]
                    String savedFileName = UUID.randomUUID().toString() + ext;

                    File uploadDir = getUploadDirectory(request);
                    File targetFile = new File(uploadDir, savedFileName);
                    filePart.write(targetFile.getAbsolutePath());
                    return savedFileName;
                }
            }
        } catch (Exception e) {
            System.err.println("[AniLog] 画像保存エラー: " + e.getMessage());
        }
        return null;
    }

    /**
     * Servlet 3.0 互換 content-disposition ヘッダーパーサー
     * 【理由】Tomcat 7 環境下で getSubmittedFileName() が未サポートの場合のエラーを回避[cite: 1]
     */
    private String extractFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        if (contentDisp == null) return null;

        for (String token : contentDisp.split(";")) {
            if (token.trim().startsWith("filename")) {
                String fileName = token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
                return fileName.substring(fileName.lastIndexOf('/') + 1).substring(fileName.lastIndexOf('\\') + 1);
            }
        }
        return null;
    }

    /**
     * 物理ファイル削除ヘルパー
     * 【機能】アップロードディレクトリ内の実体画像ファイルをディスクから消去[cite: 1]
     */
    private void deletePhysicalFile(HttpServletRequest request, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return;
        try {
            File uploadDir = getUploadDirectory(request);
            File file = new File(uploadDir, fileName);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) {}
    }

    /**
     * アップロードディレクトリの動的決定
     * 【優先順位】
     *   1. 開発環境のソースフォルダ (src/main/webapp/uploads)[cite: 1]
     *   2. 実行時Webアプリケーションパス (getRealPath)[cite: 1]
     *   3. OSの一時ディレクトリ (java.io.tmpdir)[cite: 1]
     */
    private File getUploadDirectory(HttpServletRequest request) {
        String realPath = request.getServletContext().getRealPath("/uploads");
        File dir = null;

        String appRoot = System.getProperty("user.dir");
        File devDir = new File(appRoot, "src" + File.separator + "main" + File.separator + "webapp" + File.separator + "uploads");
        if (devDir.exists() || devDir.getParentFile().exists()) {
            dir = devDir;
        } else if (realPath != null) {
            dir = new File(realPath);
        } else {
            dir = new File(System.getProperty("java.io.tmpdir"), "jpanime_uploads");
        }

        if (!dir.exists()) {
            dir.mkdirs(); // ディレクトリが存在しない場合は自動作成[cite: 1]
        }
        return dir;
    }

    /**
     * 評価値パースヘルパー (無効値時はデフォルト5)[cite: 1]
     */
    private int parseRating(String ratingStr) {
        try {
            return Integer.parseInt(ratingStr);
        } catch (Exception e) {
            return 5;
        }
    }

    /**
     * CLOBストリーム読み込みヘルパー[cite: 1]
     */
    private String readClob(Clob clob) {
        if (clob == null) return "";
        StringBuilder sb = new StringBuilder();
        try (Reader reader = clob.getCharacterStream();
             BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception ignored) {}
        return sb.toString().trim();
    }

    /**
     * JSON構文破壊防止エスケープ処理[cite: 1]
     */
    private String escapeJson(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}