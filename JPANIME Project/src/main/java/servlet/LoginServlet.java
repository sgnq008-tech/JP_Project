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
import common.PasswordUtil;

/**
 * 【サーブレット名】LoginServlet
 * 【URLマッピング】/api/login
 * 【機能概要】
 *   1. ログイン認証要求(POST)の処理
 *   2. USERS テーブルの BCrypt ハッシュとの照合 (PasswordUtil)
 *   3. 旧仕様(平文保存)のユーザーは初回ログイン成功時に BCrypt へ自動移行 (遅延マイグレーション)
 *   4. セッション固定攻撃対策 (認証成功時に既存セッションを破棄して再発行)
 *
 * 【セキュリティ上の変更点 (v2)】
 *   - 「審査用デモアカウントのバイパスログイン」を廃止 (DBに依存せず認証できる裏口だったため)。
 *     デモアカウントは SQL のシードデータとして登録され、通常のログイン経路で認証される。
 *   - 失敗時ログにパスワードを出力しない。
 *   - ユーザーIDの大文字小文字は区別する (主キーと同じ扱い)。
 */
@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        // userId / id 、 userPw / password の両方の名前を受け付ける (後方互換)
        String userId = firstNonEmpty(request.getParameter("userId"), request.getParameter("id"));
        String userPw = firstNonEmpty(request.getParameter("userPw"), request.getParameter("password"));

        if (userId == null || userPw == null) {
            response.sendRedirect(request.getContextPath() + "/login.html?error=1");
            return;
        }
        userId = userId.trim();
        userPw = userPw.trim(); // 登録時(JoinServlet)も trim して保存しているため揃える

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                System.err.println("[LoginServlet] DB接続に失敗しました。");
                response.sendRedirect(request.getContextPath() + "/login.html?error=server");
                return;
            }

            pstmt = conn.prepareStatement("SELECT USER_PW, USER_NAME FROM USERS WHERE USER_ID = ?");
            pstmt.setString(1, userId);
            rs = pstmt.executeQuery();

            boolean authenticated = false;
            boolean needsUpgrade = false;
            String userName = null;

            if (rs.next()) {
                String stored = rs.getString("USER_PW");
                userName = rs.getString("USER_NAME");

                if (PasswordUtil.verify(userPw, stored)) {
                    authenticated = true;
                } else if (PasswordUtil.matchesLegacyPlaintext(userPw, stored)) {
                    authenticated = true;
                    needsUpgrade = true; // 平文保存だった既存ユーザー → ハッシュへ移行
                }
            } else {
                // ユーザーが存在しない場合も同程度の計算コストをかけ、応答時間からIDの存在を推測されにくくする
                PasswordUtil.verify(userPw, DUMMY_HASH);
            }

            if (!authenticated) {
                // パスワードやIDの内容はログに残さない
                System.out.println("[LoginServlet] ログイン失敗 (認証情報の不一致)");
                response.sendRedirect(request.getContextPath() + "/login.html?error=1");
                return;
            }

            if (needsUpgrade) {
                upgradePasswordHash(conn, userId, userPw);
            }

            createSession(request, userId, userName);
            System.out.println("[LoginServlet] ログイン成功: " + userId);
            response.sendRedirect(request.getContextPath() + "/board.html");

        } catch (Exception e) {
            System.err.println("[LoginServlet] SQL例外発生: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/login.html?error=server");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    /** 平文だったパスワードを BCrypt ハッシュに置き換える。失敗してもログイン自体は成功扱い。 */
    private void upgradePasswordHash(Connection conn, String userId, String plainPw) {
        try (PreparedStatement up = conn.prepareStatement("UPDATE USERS SET USER_PW = ? WHERE USER_ID = ?")) {
            up.setString(1, PasswordUtil.hash(plainPw));
            up.setString(2, userId);
            up.executeUpdate();
            System.out.println("[LoginServlet] パスワードを BCrypt へ移行しました: " + userId);
        } catch (Exception e) {
            System.err.println("[LoginServlet] パスワード移行に失敗しました: " + e.getMessage());
        }
    }

    // 存在しないユーザー向けのダミー照合に使う固定ハッシュ ("dummy" 相当・形式のみ有効)
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BUV6HfHGmnbYX5rSLqvJ9V0zXe0W";

    /**
     * ログインセッション生成
     *   1. 既存セッションを破棄 (セッション固定化攻撃の防止)
     *   2. 新規セッションにユーザーIDと表示名をセット
     *   3. 有効期間 60分
     */
    private void createSession(HttpServletRequest request, String userId, String userName) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("loginId", userId);
        newSession.setAttribute("loginName", userName);
        newSession.setMaxInactiveInterval(60 * 60);
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        if (b != null && !b.trim().isEmpty()) return b;
        return null;
    }
}
