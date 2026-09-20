package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import common.DBConn;
import common.PasswordUtil;

/**
 * 【サーブレット名】JoinServlet
 * 【URLマッピング】/api/join
 * 【機能概要】
 *   1. 新規会員登録要求(POST)の処理
 *   2. 入力値の検証 (ID: 英数字4〜20文字 / パスワード: 4〜72文字 / 表示名: 1〜30文字)
 *   3. パスワードを BCrypt でハッシュ化して USERS.USER_PW に保存 (平文は保存しない)
 *   4. 主キー重複(ORA-00001)とその他のDBエラーを区別してリダイレクト
 *
 * 【v2 の変更点】
 *   - パスワードの平文保存を廃止 (BCrypt)。PASSWORD 互換カラムへの二重書き込みも廃止。
 *   - ID の形式チェックをサーバー側でも実施 (従来はクライアント側のみ)。
 *   - 例外を全て「ID重複」として扱っていた挙動を修正 (重複は ErrorCode 1 のときのみ)。
 *   ※ パスワード上限72文字は BCrypt が先頭72バイトしか使用しないため。
 */
@WebServlet("/api/join")
public class JoinServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,20}$");
    private static final int PW_MIN = 4;
    private static final int PW_MAX = 72;
    private static final int NAME_MAX = 30;
    private static final int ORA_UNIQUE_VIOLATION = 1;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String userId = request.getParameter("userId");
        String userPw = request.getParameter("userPw");
        String userName = request.getParameter("userName");

        // [1] 必須チェック
        if (isBlank(userId) || isBlank(userPw) || isBlank(userName)) {
            response.sendRedirect(request.getContextPath() + "/join.html?error=empty");
            return;
        }
        userId = userId.trim();
        userPw = userPw.trim();
        userName = userName.trim();

        // [2] 形式チェック
        if (!ID_PATTERN.matcher(userId).matches()
                || userPw.length() < PW_MIN || userPw.length() > PW_MAX
                || userName.length() > NAME_MAX) {
            response.sendRedirect(request.getContextPath() + "/join.html?error=invalid");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                System.err.println("[JoinServlet] DB接続に失敗しました。");
                response.sendRedirect(request.getContextPath() + "/login.html?error=server");
                return;
            }

            // [3] BCrypt ハッシュを保存 (PASSWORD 互換カラムは使用しない)
            pstmt = conn.prepareStatement("INSERT INTO USERS (USER_ID, USER_PW, USER_NAME) VALUES (?, ?, ?)");
            pstmt.setString(1, userId);
            pstmt.setString(2, PasswordUtil.hash(userPw));
            pstmt.setString(3, userName);

            if (pstmt.executeUpdate() > 0) {
                System.out.println("[JoinServlet] 新規登録成功: " + userId);
                response.sendRedirect(request.getContextPath() + "/login.html?registered=1");
            } else {
                response.sendRedirect(request.getContextPath() + "/login.html?error=join_fail");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == ORA_UNIQUE_VIOLATION) {
                response.sendRedirect(request.getContextPath() + "/login.html?error=duplicate");
            } else {
                System.err.println("[JoinServlet] 会員登録エラー: " + e.getMessage());
                response.sendRedirect(request.getContextPath() + "/login.html?error=server");
            }
        } catch (Exception e) {
            System.err.println("[JoinServlet] 予期しないエラー: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/login.html?error=server");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
