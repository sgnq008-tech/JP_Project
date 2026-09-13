package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import common.DBConn;

/**
 * 【サーブレット名】JoinServlet
 * 【URLマッピング】/api/join
 * 【機能概要】
 *   1. フロントエンド(join.html)からの新規ヒーロー(会員)登録要求(POST)を処理
 *   2. 入力必須パラメータ(ユーザーID、パスワード、ニックネーム)の検証とトリム処理
 *   3. Oracle DB(USERSテーブル)への新規レコードINSERT
 *   4. トランザクション確定(setAutoCommit)の明示的保証によるデータ永続化
 *   5. 主キー(PK)重複エラーやDB例外の検知と適切なリダイレクト分岐
 */
@WebServlet("/api/join")
public class JoinServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * 新規会員登録処理 (POSTリクエスト)
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // [1] リクエストボディの文字エンコーディングをUTF-8に設定 (日本語・韓国語の文字化け防止)
        request.setCharacterEncoding("UTF-8");

        // [2] フォーム送信された各入力パラメータの取得
        String userId = request.getParameter("userId");
        String userPw = request.getParameter("userPw");
        String userName = request.getParameter("userName");

        // [3] 入力必須チェック: 未入力または空白のみの場合は会員登録画面へエラーリダイレクト
        if (userId == null || userId.trim().isEmpty() ||
                userPw == null || userPw.trim().isEmpty() ||
                userName == null || userName.trim().isEmpty()) {
            System.err.println("[JoinServlet] ⚠️ 入力パラメータの不足を検知しました。");
            response.sendRedirect(request.getContextPath() + "/join.html?error=empty");
            return;
        }

        // 前後の不要なホワイトスペースを除去
        userId = userId.trim();
        userPw = userPw.trim();
        userName = userName.trim();

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            // [4] 自動フォールバック対応の共通DBコネクション取得 (xe / orcl / xepdb1)
            conn = DBConn.getConnection();
            if (conn == null) {
                System.err.println("[JoinServlet] ❌ DB接続に失敗しました。(コネクションがnullです)");
                response.sendRedirect(request.getContextPath() + "/login.html?error=server");
                return;
            }

            // [5] 自動コミットの有効化を保証 (接続環境によるロールバックや永続化漏れを防止)
            conn.setAutoCommit(true);

            // [6] レコード登録SQL: 各サーブレットの参照差異を吸収するため、USER_PWとPASSWORD両カラムにパスワードを格納
            String sql = "INSERT INTO USERS (USER_ID, USER_PW, PASSWORD, USER_NAME) VALUES (?, ?, ?, ?)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, userId);
            pstmt.setString(2, userPw);
            pstmt.setString(3, userPw);
            pstmt.setString(4, userName);

            // [7] SQL実行および更新件数の取得
            int result = pstmt.executeUpdate();

            if (result > 0) {
                // 登録成功: ログイン画面へ ?registered=1 フラグを付与してリダイレクト (完了バナー表示)
                System.out.println("[JoinServlet] 🌟 新規ヒーロー登録成功! ID: " + userId + ", Name: " + userName);
                response.sendRedirect(request.getContextPath() + "/login.html?registered=1");
            } else {
                // INSERT件数が0件の場合の異常処理
                System.err.println("[JoinServlet] ❌ レコード追加失敗 (影響行数: 0)");
                response.sendRedirect(request.getContextPath() + "/login.html?error=join_fail");
            }
        } catch (Exception e) {
            // [8] 主キー(USER_ID)重複制約違反(ORA-00001)またはSQL実行例外の検知
            System.err.println("[JoinServlet] ❌ 会員登録エラー発生 (ID重複の可能性あり): " + e.getMessage());
            e.printStackTrace();
            // 重複エラー通知用パラメータを付与してログイン画面へリダイレクト
            response.sendRedirect(request.getContextPath() + "/login.html?error=duplicate");
        } finally {
            // [9] リソースリーク防止のためStatementとConnectionを安全にクローズ
            DBConn.close(pstmt, conn);
        }
    }
}