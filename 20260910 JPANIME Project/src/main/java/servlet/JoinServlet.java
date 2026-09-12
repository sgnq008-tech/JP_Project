package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import common.DBConn;

@WebServlet("/api/join")
public class JoinServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String userId = request.getParameter("userId");
        String userPw = request.getParameter("userPw");
        String userName = request.getParameter("userName");

        if (userId == null || userId.trim().isEmpty() ||
                userPw == null || userPw.trim().isEmpty() ||
                userName == null || userName.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/join.html?error=empty");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                System.err.println("[JoinServlet] ❌ DB 커넥션을 가져오지 못했습니다. DBConn 설정을 확인하세요.");
                response.sendRedirect(request.getContextPath() + "/join.html?error=fail");
                return;
            }

            String sql = "INSERT INTO USERS (USER_ID, USER_PW, USER_NAME) VALUES (?, ?, ?)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, userId.trim());
            pstmt.setString(2, userPw.trim());
            pstmt.setString(3, userName.trim());

            int result = pstmt.executeUpdate();
            if (result > 0) {
                System.out.println("[JoinServlet] ✅ 회원가입 성공 ID: " + userId.trim());
                response.sendRedirect(request.getContextPath() + "/login.html");
            } else {
                response.sendRedirect(request.getContextPath() + "/join.html?error=fail");
            }
        } catch (SQLException e) {
            System.err.println("[JoinServlet] ❌ SQL 에러 (코드: " + e.getErrorCode() + "): " + e.getMessage());
            e.printStackTrace();
            if (e.getErrorCode() == 1) { // ORA-00001: 중복 아이디
                response.sendRedirect(request.getContextPath() + "/join.html?error=duplicate");
            } else {
                response.sendRedirect(request.getContextPath() + "/join.html?error=fail");
            }
        } catch (Exception e) {
            System.err.println("[JoinServlet] ❌ 일반 에러: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/join.html?error=fail");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }
}