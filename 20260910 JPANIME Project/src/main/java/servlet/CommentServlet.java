package servlet;

import java.io.IOException;
import java.io.PrintWriter;
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

    // 댓글 목록 조회 (GET)
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        String bnoStr = request.getParameter("bno");
        if (bnoStr == null || bnoStr.trim().isEmpty()) {
            response.getWriter().write("[]");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        StringBuilder json = new StringBuilder("[");

        try {
            conn = DBConn.getConnection();
            String sql = "SELECT CNO, BNO, WRITER, CONTENT, TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE "
                    + "FROM REVIEW_COMMENTS WHERE BNO = ? ORDER BY CNO ASC";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, Integer.parseInt(bnoStr));
            rs = pstmt.executeQuery();

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append("{")
                        .append("\"cno\":").append(rs.getInt("CNO")).append(",")
                        .append("\"bno\":").append(rs.getInt("BNO")).append(",")
                        .append("\"writer\":\"").append(escapeJson(rs.getString("WRITER"))).append("\",")
                        .append("\"content\":\"").append(escapeJson(rs.getString("CONTENT"))).append("\",")
                        .append("\"regDate\":\"").append(rs.getString("REG_DATE")).append("\"")
                        .append("}");
                first = false;
            }
            json.append("]");

            PrintWriter out = response.getWriter();
            out.print(json.toString());
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("[]");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    // 댓글 등록 (POST)
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        // 로그인 세션 확인
        HttpSession session = request.getSession(false);
        String loginId = (session != null) ? (String) session.getAttribute("loginId") : null;

        if (loginId == null || loginId.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false, \"message\":\"로그인이 필요합니다.\"}");
            return;
        }

        String bnoStr = request.getParameter("bno");
        String content = request.getParameter("content");

        if (bnoStr == null || content == null || content.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false, \"message\":\"입력값이 부족합니다.\"}");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
            String sql = "INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE) "
                    + "VALUES (SEQ_COMMENT_CNO.NEXTVAL, ?, ?, ?, SYSDATE)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, Integer.parseInt(bnoStr));
            pstmt.setString(2, loginId.trim());
            pstmt.setString(3, content.trim());

            int result = pstmt.executeUpdate();
            if (result > 0) {
                response.getWriter().write("{\"success\":true}");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"success\":false}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false, \"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}