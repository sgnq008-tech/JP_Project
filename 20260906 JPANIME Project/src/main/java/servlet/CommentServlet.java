package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import common.DBConn;

@WebServlet("/api/comments")
public class CommentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // GET: 特定レビュー(bno)のコメント一覧をJSON形式で返却
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // 1. レスポンス設定およびキャッシュ無効化（リアルタイム反映のため）
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        String bnoParam = request.getParameter("bno");
        int bno = 0;
        try {
            if (bnoParam != null) {
                bno = Integer.parseInt(bnoParam.trim());
            }
        } catch (NumberFormatException e) {
            response.getWriter().write("[]");
            return;
        }

        if (bno <= 0) {
            response.getWriter().write("[]");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        StringBuilder json = new StringBuilder("[");

        try {
            conn = DBConn.getConnection();
            String sql = "SELECT CNO, BNO, WRITER, CONTENT, " +
                    "TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE " +
                    "FROM REVIEW_COMMENTS WHERE BNO = ? ORDER BY CNO ASC";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, bno);
            rs = pstmt.executeQuery();

            boolean isFirst = true;
            while (rs.next()) {
                if (!isFirst) json.append(",");
                json.append("{")
                        .append("\"cno\":").append(rs.getInt("CNO")).append(",")
                        .append("\"bno\":").append(rs.getInt("BNO")).append(",")
                        .append("\"writer\":\"").append(escapeJson(rs.getString("WRITER"))).append("\",")
                        .append("\"content\":\"").append(escapeJson(rs.getString("CONTENT"))).append("\",")
                        .append("\"regDate\":\"").append(rs.getString("REG_DATE")).append("\"")
                        .append("}");
                isFirst = false;
            }
        } catch (Exception e) {
            System.err.println("[AniLog CommentServlet GET Error] " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
        json.append("]");
        response.getWriter().write(json.toString());
    }

    // POST: 新規コメント登録処理
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        HttpSession session = request.getSession(false);
        String writer = (session != null) ? (String) session.getAttribute("loginId") : null;

        // ログイン状態検証
        if (writer == null || writer.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"error\":\"unauthorized\"}");
            return;
        }

        String bnoParam = request.getParameter("bno");
        String content = request.getParameter("content");

        int bno = 0;
        try {
            if (bnoParam != null) {
                bno = Integer.parseInt(bnoParam.trim());
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"error\":\"invalid_bno\"}");
            return;
        }

        if (bno <= 0 || content == null || content.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"error\":\"empty_content\"}");
            return;
        }

        // XSS防止サニタイズ処理
        String sanitizedContent = sanitizeHtml(content.trim());

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
            String sql = "INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE) " +
                    "VALUES (SEQ_COMMENT_CNO.NEXTVAL, ?, ?, ?, SYSDATE)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, bno);
            pstmt.setString(2, writer);
            pstmt.setString(3, sanitizedContent);
            pstmt.executeUpdate();

            response.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            System.err.println("[AniLog CommentServlet POST Error] " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"error\":\"db_error\"}");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    // XSS防止HTMLサニタイズ
    private String sanitizeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    // JSON文字列エスケープ
    private String escapeJson(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}