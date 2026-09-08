package servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // GET: ログイン中のユーザーセッション情報（IDおよび表示名）をJSON形式で返却
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        HttpSession session = request.getSession(false);

        if (session != null && session.getAttribute("loginId") != null) {
            String loginId = (String) session.getAttribute("loginId");
            String loginName = (String) session.getAttribute("loginName");
            if (loginName == null) loginName = loginId;
            
            // JSONフォーマットでクライアントへ返却
            response.getWriter().write(String.format("{\"loginId\":\"%s\",\"loginName\":\"%s\"}", loginId, loginName));
        } else {
            // 未ログイン状態
            response.getWriter().write("{}");
        }
    }

    // POST: ログアウト処理（既存セッションの破棄）
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate(); // セッション破棄
        }
        // ログイン画面へリダイレクト
        response.sendRedirect(request.getContextPath() + "/login.html");
    }
}