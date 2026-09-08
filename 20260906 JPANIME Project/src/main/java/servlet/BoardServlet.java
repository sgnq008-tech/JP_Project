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

@WebServlet("/api/board")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 1, // 1MB
    maxFileSize = 1024 * 1024 * 10,      // 10MB
    maxRequestSize = 1024 * 1024 * 20    // 20MB
)
public class BoardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // GET: アニメレビュー一覧取得または単一レビューの詳細取得
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");

        String bnoParam = request.getParameter("bno");
        if (bnoParam != null && !bnoParam.trim().isEmpty()) {
            getSingleReview(request, response, Integer.parseInt(bnoParam));
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        StringBuilder json = new StringBuilder("[");

        try {
            conn = DBConn.getConnection();
            String sql = "SELECT BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, WRITER, " +
                         "TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE " +
                         "FROM ANIME_REVIEWS ORDER BY BNO DESC";
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();

            boolean isFirst = true;
            while (rs.next()) {
                if (!isFirst) json.append(",");
                
                String contentStr = readClob(rs.getClob("CONTENT"));

                json.append("{")
                    .append("\"bno\":").append(rs.getInt("BNO")).append(",")
                    .append("\"animeTitle\":\"").append(escapeJson(rs.getString("ANIME_TITLE"))).append("\",")
                    .append("\"title\":\"").append(escapeJson(rs.getString("TITLE"))).append("\",")
                    .append("\"content\":\"").append(escapeJson(contentStr)).append("\",")
                    .append("\"rating\":").append(rs.getInt("RATING")).append(",")
                    .append("\"imageFile\":\"").append(escapeJson(rs.getString("IMAGE_FILE"))).append("\",")
                    .append("\"writer\":\"").append(escapeJson(rs.getString("WRITER"))).append("\",")
                    .append("\"regDate\":\"").append(rs.getString("REG_DATE")).append("\"")
                    .append("}");
                isFirst = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
        json.append("]");
        response.getWriter().write(json.toString());
    }

    // 単一レビューのJSON返却
    private void getSingleReview(HttpServletRequest request, HttpServletResponse response, int bno) 
            throws IOException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DBConn.getConnection();
            String sql = "SELECT BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, WRITER, " +
                         "TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE " +
                         "FROM ANIME_REVIEWS WHERE BNO = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, bno);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                String contentStr = readClob(rs.getClob("CONTENT"));
                String json = String.format(
                    "{\"bno\":%d,\"animeTitle\":\"%s\",\"title\":\"%s\",\"content\":\"%s\",\"rating\":%d,\"imageFile\":\"%s\",\"writer\":\"%s\",\"regDate\":\"%s\"}",
                    rs.getInt("BNO"),
                    escapeJson(rs.getString("ANIME_TITLE")),
                    escapeJson(rs.getString("TITLE")),
                    escapeJson(contentStr),
                    rs.getInt("RATING"),
                    escapeJson(rs.getString("IMAGE_FILE")),
                    escapeJson(rs.getString("WRITER")),
                    rs.getString("REG_DATE")
                );
                response.getWriter().write(json);
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

    // POST: アニメレビューの新規登録、修正、削除
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
            // レビュー削除処理
            deleteReview(request, response, writer);
            return;
        } else if ("update".equals(action)) {
            // レビュー修正処理
            updateReview(request, response, writer);
            return;
        }

        // 新規登録処理
        insertReview(request, response, writer);
    }

    // レビュー削除処理 (DB削除および物理画像ファイル削除)
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
            // 削除対象の画像ファイル名を取得
            String findSql = "SELECT IMAGE_FILE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?";
            pstmt = conn.prepareStatement(findSql);
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                imageFileToDelete = rs.getString("IMAGE_FILE");
            }
            pstmt.close();

            // レビューの削除 (本人確認 WRITER = ?)
            String deleteSql = "DELETE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?";
            pstmt = conn.prepareStatement(deleteSql);
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            int affected = pstmt.executeUpdate();

            // レビュー削除成功時、サーバー上の実画像ファイルも削除して容量を確保
            if (affected > 0 && imageFileToDelete != null && !imageFileToDelete.trim().isEmpty()) {
                String uploadPath = request.getServletContext().getRealPath("/uploads");
                if (uploadPath != null) {
                    File file = new File(uploadPath + File.separator + imageFileToDelete);
                    if (file.exists()) {
                        file.delete();
                    }
                }
            }

            response.sendRedirect(request.getContextPath() + "/board.html");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/board.html?error=delete_fail");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    // 新規登録処理
    private void insertReview(HttpServletRequest request, HttpServletResponse response, String writer) 
            throws IOException {
        String animeTitle = request.getParameter("animeTitle");
        String title = request.getParameter("title");
        String content = request.getParameter("content");
        int rating = parseRating(request.getParameter("rating"));

        String savedFileName = saveUploadedFile(request);

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
            String sql = "INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, WRITER, REG_DATE) " +
                         "VALUES (SEQ_REVIEW_BNO.NEXTVAL, ?, ?, ?, ?, ?, ?, SYSDATE)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, animeTitle != null ? animeTitle.trim() : "Untitled");
            pstmt.setString(2, title != null ? title.trim() : "No Title");
            pstmt.setString(3, content != null ? content : "");
            pstmt.setInt(4, rating);
            pstmt.setString(5, savedFileName);
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

    // レビュー修正処理
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

        String newUploadedFile = saveUploadedFile(request);
        String finalImage = (newUploadedFile != null) ? newUploadedFile : existingImage;

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
            String sql = "UPDATE ANIME_REVIEWS " +
                         "SET ANIME_TITLE = ?, TITLE = ?, CONTENT = ?, RATING = ?, IMAGE_FILE = ? " +
                         "WHERE BNO = ? AND WRITER = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, animeTitle != null ? animeTitle.trim() : "Untitled");
            pstmt.setString(2, title != null ? title.trim() : "No Title");
            pstmt.setString(3, content != null ? content : "");
            pstmt.setInt(4, rating);
            pstmt.setString(5, finalImage);
            pstmt.setInt(6, bno);
            pstmt.setString(7, writer);

            pstmt.executeUpdate();
            response.sendRedirect(request.getContextPath() + "/board.html");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/board.html?error=update_db");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    // ファイルアップロード保存共通ヘルパー
    private String saveUploadedFile(HttpServletRequest request) {
        try {
            Part filePart = request.getPart("animeImage");
            if (filePart != null && filePart.getSize() > 0) {
                String submittedName = filePart.getSubmittedFileName();
                if (submittedName != null && !submittedName.trim().isEmpty()) {
                    String fileNameOnly = Paths.get(submittedName).getFileName().toString();
                    String ext = "";
                    int dotIdx = fileNameOnly.lastIndexOf(".");
                    if (dotIdx >= 0) ext = fileNameOnly.substring(dotIdx);
                    String savedFileName = UUID.randomUUID().toString() + ext;

                    String uploadPath = request.getServletContext().getRealPath("/uploads");
                    if (uploadPath == null) uploadPath = System.getProperty("java.io.tmpdir");
                    File uploadDir = new File(uploadPath);
                    if (!uploadDir.exists()) uploadDir.mkdirs();

                    filePart.write(uploadPath + File.separator + savedFileName);
                    return savedFileName;
                }
            }
        } catch (Exception e) {
            System.err.println("[AniLog] 画像保存警告: " + e.getMessage());
        }
        return null;
    }

    private int parseRating(String ratingStr) {
        try {
            return Integer.parseInt(ratingStr);
        } catch (Exception e) {
            return 5;
        }
    }

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

    private String escapeJson(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\r", "")
                  .replace("\n", "\\n");
    }
}