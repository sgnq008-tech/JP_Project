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
            try {
                getSingleReview(request, response, Integer.parseInt(bnoParam.trim()));
            } catch (NumberFormatException e) {
                response.getWriter().write("{}");
            }
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
            deleteReview(request, response, writer);
            return;
        } else if ("update".equals(action)) {
            updateReview(request, response, writer);
            return;
        }

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
            String findSql = "SELECT IMAGE_FILE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?";
            pstmt = conn.prepareStatement(findSql);
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                imageFileToDelete = rs.getString("IMAGE_FILE");
            }
            pstmt.close();

            String deleteSql = "DELETE FROM ANIME_REVIEWS WHERE BNO = ? AND WRITER = ?";
            pstmt = conn.prepareStatement(deleteSql);
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            int affected = pstmt.executeUpdate();

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
        String finalImage = (newUploadedFile != null) ? newUploadedFile : (existingImage != null ? existingImage : "");

        // 新しい画像が登録された場合、以前の古い画像ファイルがあれば物理削除
        if (newUploadedFile != null && existingImage != null && !existingImage.trim().isEmpty()) {
            deletePhysicalFile(request, existingImage);
        }

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
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
            response.sendRedirect(request.getContextPath() + "/board.html");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/board.html?error=update_db");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    // ファイルアップロード保存共通ヘルパー (Tomcat 7 / Servlet 3.0 호환)
    private String saveUploadedFile(HttpServletRequest request) {
        try {
            Part filePart = request.getPart("animeImage");
            if (filePart != null && filePart.getSize() > 0) {
                // Servlet 3.0 호환 파일명 추출
                String submittedName = extractFileName(filePart);
                if (submittedName != null && !submittedName.trim().isEmpty()) {
                    String fileNameOnly = Paths.get(submittedName).getFileName().toString();
                    String ext = "";
                    int dotIdx = fileNameOnly.lastIndexOf(".");
                    if (dotIdx >= 0) {
                        ext = fileNameOnly.substring(dotIdx).toLowerCase();
                    }
                    String savedFileName = UUID.randomUUID().toString() + ext;

                    File uploadDir = getUploadDirectory(request);
                    File targetFile = new File(uploadDir, savedFileName);
                    filePart.write(targetFile.getAbsolutePath());

                    System.out.println("[AniLog] ファイル保存成功: " + targetFile.getAbsolutePath());
                    return savedFileName;
                }
            }
        } catch (Exception e) {
            System.err.println("[AniLog] 画像保存警告: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // Servlet 3.0 호환 Multipart Header 파싱 메서드 (Tomcat 7 NoSuchMethodError 방지)
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

    // 物理ファイル削除ヘルパー
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

    // アップロードディレクトリの決定
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
            dir.mkdirs();
        }
        return dir;
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