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

@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String userId = request.getParameter("userId");
        String userPw = request.getParameter("userPw");

        if (userId == null || userId.trim().isEmpty() ||
                userPw == null || userPw.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/login.html?error=1");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                System.err.println("[LoginServlet] ❌ DB 커넥션을 가져오지 못했습니다.");
                response.sendRedirect(request.getContextPath() + "/login.html?error=server");
                return;
            }

            String sql = "SELECT USER_NAME FROM USERS WHERE USER_ID = ? AND USER_PW = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, userId.trim());
            pstmt.setString(2, userPw.trim());
            rs = pstmt.executeQuery();

            if (rs.next()) {
                String userName = rs.getString("USER_NAME");

                HttpSession oldSession = request.getSession(false);
                if (oldSession != null) oldSession.invalidate();

                HttpSession newSession = request.getSession(true);
                newSession.setAttribute("loginId", userId.trim());
                newSession.setAttribute("loginName", userName);
                newSession.setMaxInactiveInterval(60 * 60);

                System.out.println("[LoginServlet] ✅ 로그인 성공: " + userId.trim() + " (" + userName + ")");
                response.sendRedirect(request.getContextPath() + "/board.html");
            } else {
                System.out.println("[LoginServlet] ⚠️ 로그인 실패 (일치 계정 없음): " + userId.trim());
                response.sendRedirect(request.getContextPath() + "/login.html?error=1");
            }
        } catch (Exception e) {
            System.err.println("[LoginServlet] ❌ 에러 발생: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/login.html?error=server");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }
}