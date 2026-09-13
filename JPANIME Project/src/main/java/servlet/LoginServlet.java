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

/**
 * 【サーブレット名】LoginServlet
 * 【URLマッピング】/api/login
 * 【機能概要】
 *   1. フロントエンドからのログイン認証要求(POST)を処理
 *   2. ポートフォリオ審査用デモアカウント(test1/admin/kenji)のバイパスログイン
 *   3. 一般会員に対するOracle DB(USERSテーブル)を用いた資格情報照合
 *   4. セッション固定攻撃(Session Fixation)対策およびログインセッションの生成
 */
@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * ログイン認証処理 (POSTリクエスト)
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // [1] リクエスト文字エンコーディングをUTF-8に設定 (日本語・韓国語の文字化け防止)
        request.setCharacterEncoding("UTF-8");

        // [2] ユーザーIDパラメータの取得 (userId または id の両方を受け入れ可能な後方互換設計)
        String userId = request.getParameter("userId");
        if (userId == null || userId.trim().isEmpty()) {
            userId = request.getParameter("id");
        }

        // [3] パスワードパラメータの取得 (userPw または password の両方を受け入れ可能)
        String userPw = request.getParameter("userPw");
        if (userPw == null || userPw.trim().isEmpty()) {
            userPw = request.getParameter("password");
        }

        // [4] 入力値の必須チェック: どちらかが空の場合は認証失敗(?error=1)としてリダイレクト
        if (userId == null || userId.trim().isEmpty() ||
                userPw == null || userPw.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/login.html?error=1");
            return;
        }

        // 前後の不要なホワイトスペースをトリム
        userId = userId.trim();
        userPw = userPw.trim();

        // =========================================================================
        // [5] ポートフォリオ審査用アカウントのバイパス処理 (マスターキー認証)
        // 理由: 審査員がローカルDB環境のテーブル未作成や未接続時でも即座に全機能を閲覧可能にするため
        // =========================================================================
        if (isDemoAccount(userId, userPw)) {
            String demoName = getDemoName(userId);
            createSession(request, userId, demoName);
            System.out.println("[LoginServlet] 🌟 審査用デモアカウントでログイン成功: " + userId + " (" + demoName + ")");
            response.sendRedirect(request.getContextPath() + "/board.html");
            return;
        }

        // =========================================================================
        // [6] 一般会員向け Oracle DB 資格情報照合
        // =========================================================================
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            // 自動フォールバック機能付きDBコネクション取得 (xe / orcl / xepdb1)
            conn = DBConn.getConnection();
            if (conn == null) {
                System.err.println("[LoginServlet] ❌ DB接続に失敗しました。(コネクションがnullです)");
                response.sendRedirect(request.getContextPath() + "/login.html?error=server");
                return;
            }

            // 大文字小文字の違い(LOWER)や前後の余分な空白(TRIM)を無視して照合
            // カラム定義差異(USER_PW / PASSWORD)の両方に対応
            String sql = "SELECT USER_NAME FROM USERS "
                    + "WHERE LOWER(TRIM(USER_ID)) = LOWER(?) "
                    + "  AND (TRIM(USER_PW) = ? OR TRIM(PASSWORD) = ?)";

            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, userId);
            pstmt.setString(2, userPw);
            pstmt.setString(3, userPw);

            rs = pstmt.executeQuery();

            if (rs.next()) {
                // 認証成功: DBから登録されたヒーローネーム(ニックネーム)を取得してセッションを生成
                String userName = rs.getString("USER_NAME");
                createSession(request, userId, userName);
                System.out.println("[LoginServlet] ✅ DB会員ログイン成功: " + userId + " (" + userName + ")");
                response.sendRedirect(request.getContextPath() + "/board.html");
            } else {
                // 認証失敗: 該当するユーザー情報が存在しない場合
                System.out.println("[LoginServlet] ⚠️ ログイン失敗 - アカウント不一致: ID=[" + userId + "], PW=[" + userPw + "]");
                response.sendRedirect(request.getContextPath() + "/login.html?error=1");
            }
        } catch (Exception e) {
            // SQL構文エラーやネットワークエラー等の例外発生時
            System.err.println("[LoginServlet] ❌ SQL例外発生: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/login.html?error=server");
        } finally {
            // リソースリーク防止のためResultSet, PreparedStatement, Connectionを安全にクローズ
            DBConn.close(rs, pstmt, conn);
        }
    }

    /**
     * ポートフォリオ審査用デモアカウント判定ヘルパー
     * @param id ユーザーID
     * @param pw パスワード
     * @return デモアカウントに合致する場合 true
     */
    private boolean isDemoAccount(String id, String pw) {
        if ("test1".equalsIgnoreCase(id) && "1234".equals(pw)) return true;
        if ("admin".equalsIgnoreCase(id) && "1234".equals(pw)) return true;
        if ("kenji".equalsIgnoreCase(id) && "1234".equals(pw)) return true;
        return false;
    }

    /**
     * 審査用アカウントのヒーローネーム(表示名)マッピング
     * @param id ユーザーID
     * @return マッピングされた表示名
     */
    private String getDemoName(String id) {
        if ("admin".equalsIgnoreCase(id)) return "All Might (管理者)";
        if ("kenji".equalsIgnoreCase(id)) return "Kenji (ケンジ)";
        return "Deku (審査員)";
    }

    /**
     * ログインセッション生成共通メソッド
     * 【セキュリティ対策】
     *   1. 既存セッションが存在する場合は invalidate() で破棄 (セッション固定化攻撃の防止)
     *   2. 新規セッションを発行し、ユーザーIDと表示名を属性にセット
     *   3. セッション有効期間を60分(3600秒)に設定
     */
    private void createSession(HttpServletRequest request, String userId, String userName) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("loginId", userId);
        newSession.setAttribute("loginName", userName);
        newSession.setMaxInactiveInterval(60 * 60); // 60分間有効
    }
}