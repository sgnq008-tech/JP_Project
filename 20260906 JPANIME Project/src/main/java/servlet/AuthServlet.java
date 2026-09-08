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
        // 1. レスポンス設定およびキャッシュ無効化（ログアウト後の状態不整合防止）
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
        response.setHeader("Pragma", "no-cache");                                  // HTTP 1.0
        response.setDateHeader("Expires", 0);                                       // Proxies

        HttpSession session = request.getSession(false);

        if (session != null && session.getAttribute("loginId") != null) {
            String loginId = (String) session.getAttribute("loginId");
            String loginName = (String) session.getAttribute("loginName");
            if (loginName == null || loginName.trim().isEmpty()) {
                loginName = loginId;
            }

            // JSON安全エスケープ処理を施してクライアントへ返却
            String json = String.format(
                    "{\"loginId\":\"%s\",\"loginName\":\"%s\"}",
                    escapeJson(loginId),
                    escapeJson(loginName)
            );
            response.getWriter().write(json);
        } else {
            // 未ログイン状態の返却
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

    // JSON文字列エスケープヘルパー
    private String escapeJson(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}