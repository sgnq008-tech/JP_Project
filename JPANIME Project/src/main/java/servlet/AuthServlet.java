package servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 【サーブレット名】AuthServlet
 * 【URLマッピング】/api/auth
 * 【機能概要】
 *   1. GET: 現在ログイン中のセッション情報(ユーザーID、ヒーローネーム)をJSON形式で返却
 *   2. POST: ログアウト処理 (既存セッションの破棄およびログイン画面へのリダイレクト)
 *   3. キャッシュ無効化ヘッダーの送出によるログアウト後のブラウザ「戻る」ボタンによる画面不整合防止
 *   4. JSON構文破壊防止のための特殊文字エスケープ処理
 */
@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * ログインセッション状態確認処理 (GETリクエスト)
     * 【機能】各画面(board.html, detail.html, edit.html)の初期化時に呼び出され、
     *        現在ログイン中のユーザー情報があるかを確認してUIの表示/非表示を制御
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // [1] レスポンスのMIMEタイプと文字コードをUTF-8 JSONに設定
        response.setContentType("application/json; charset=UTF-8");

        // [2] キャッシュ無効化ヘッダーの設定 (ログアウト後の誤認表示やプロキシキャッシュを完全に防止)
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1対応
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0後方互換対応
        response.setDateHeader("Expires", 0); // プロキシサーバー向け即時有効期限切れ設定

        // [3] 既存セッションの取得 (新規作成はせず、存在しない場合はnullを取得)
        HttpSession session = request.getSession(false);

        // [4] セッションが存在し、かつloginIdがセットされているかの検証
        if (session != null && session.getAttribute("loginId") != null) {
            String loginId = (String) session.getAttribute("loginId");
            String loginName = (String) session.getAttribute("loginName");

            // 特殊文字をエスケープ処理した上でログイン情報のJSONを返却
            String json = String.format(
                    "{\"loginId\":\"%s\",\"loginName\":\"%s\"}",
                    escapeJson(loginId),
                    escapeJson(loginName)
            );
            response.getWriter().write(json);
        } else {
            // 未ログイン状態: クライアント側で非ログインとして扱えるよう空オブジェクトを返却
            response.getWriter().write("{}");
        }
    }

    /**
     * ログアウト処理 (POSTリクエスト)
     * 【機能】ナビゲーションバーのログアウトボタン押下時にセッションを完全に無効化し、login.htmlへ遷移
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // [1] 既存セッションの取得
        HttpSession session = request.getSession(false);
        if (session != null) {
            // [2] セッションの完全破棄 (バインドされた全属性とセッションIDを無効化)
            session.invalidate();
        }
        // [3] ログアウト完了後、ログイン画面(login.html)へ安全にリダイレクト
        response.sendRedirect(request.getContextPath() + "/login.html");
    }

    /**
     * JSON文字列エスケープ共通ヘルパーメソッド
     * 【機能】JSON文字列のパースエラーや構文破壊を引き起こすバックスラッシュ、ダブルクォーテーション、改行文字を安全に置換
     * @param val エスケープ対象の文字列
     * @return エスケープ処理済み文字列
     */
    private String escapeJson(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}