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

/**
 * 【サーブレット名】CommentServlet
 * 【URLマッピング】/api/comments
 * 【機能概要】
 *   1. GET: 特定レビュー番号(bno)に紐づくコメント一覧をJSON形式で返却
 *   2. POST: ログイン中ユーザーによる新規コメント登録処理 (セッション検証付き)
 *   3. REVIEW_COMMENTS テーブルおよび SEQ_COMMENT_CNO シーケンスと連動
 *   4. JSON文字列生成時のエスケープ処理による構文破壊防止
 */
@WebServlet("/api/comments")
public class CommentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * コメント一覧取得処理 (GETリクエスト)
     * 【機能】クエリパラメータ ?bno=X を受け取り、該当レビューの全コメントを登録昇順で返却
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // [1] リクエスト文字コード及びレスポンスMIMEタイプをUTF-8 JSONに設定
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        // [2] 照会対象のレビュー番号(bno)を取得
        String bnoStr = request.getParameter("bno");
        if (bnoStr == null || bnoStr.trim().isEmpty()) {
            // レビュー番号が指定されていない場合は空配列を返却して終了
            response.getWriter().write("[]");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        StringBuilder json = new StringBuilder("[");

        try {
            // [3] 自動フォールバック対応のDBコネクション取得
            conn = DBConn.getConnection();

            // [4] 日時を'YYYY-MM-DD HH24:MI'形式にフォーマットしてコメント番号(CNO)順に取得
            String sql = "SELECT CNO, BNO, WRITER, CONTENT, TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE "
                    + "FROM REVIEW_COMMENTS WHERE BNO = ? ORDER BY CNO ASC";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, Integer.parseInt(bnoStr));
            rs = pstmt.executeQuery();

            boolean first = true;
            // [5] 取得結果を1件ずつJSONオブジェクト文字列へ組み立て
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

            // [6] レスポンスストリームへJSON文字列を出力
            PrintWriter out = response.getWriter();
            out.print(json.toString());
            out.flush();
        } catch (Exception e) {
            // 例外発生時はログ出力およびHTTP 500エラーコードを設定
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("[]");
        } finally {
            // [7] DBリソースの安全な解放
            DBConn.close(rs, pstmt, conn);
        }
    }

    /**
     * コメント新規登録処理 (POSTリクエスト)
     * 【機能】ログインセッションの存在を確認し、新規コメントをDBへ永続化
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // [1] 入出力エンコーディングの設定
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        // [2] ログインセッションの検証 (未ログイン状態での投稿を拒否)
        HttpSession session = request.getSession(false);
        String loginId = (session != null) ? (String) session.getAttribute("loginId") : null;

        if (loginId == null || loginId.trim().isEmpty()) {
            // 401 Unauthorized エラーを返却 (フロントエンド側でログイン画面遷移を促す)
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false, \"message\":\"UNAUTHORIZED\"}");
            return;
        }

        // [3] 送信パラメータ(レビュー番号、コメント本文)の取得
        String bnoStr = request.getParameter("bno");
        String content = request.getParameter("content");

        // 入力値検証: レビュー番号またはコメント本文が空の場合は 400 Bad Request
        if (bnoStr == null || content == null || content.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false, \"message\":\"BAD_REQUEST\"}");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBConn.getConnection();

            // [4] シーケンス(SEQ_COMMENT_CNO)を用いてコメント番号を自動採番し、INSERTを実行
            String sql = "INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE) "
                    + "VALUES (SEQ_COMMENT_CNO.NEXTVAL, ?, ?, ?, SYSDATE)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, Integer.parseInt(bnoStr));
            pstmt.setString(2, loginId.trim());
            pstmt.setString(3, content.trim());

            int result = pstmt.executeUpdate();
            if (result > 0) {
                // 登録成功: 成功JSONを返却
                response.getWriter().write("{\"success\":true}");
            } else {
                // 更新行数0件時: 内部エラー返却
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"success\":false}");
            }
        } catch (Exception e) {
            // SQLエラー等の例外発生時
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false, \"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        } finally {
            // [5] コネクションおよびステートメントのクローズ
            DBConn.close(pstmt, conn);
        }
    }

    /**
     * JSONエスケープ処理共通ヘルパー関数
     * 【機能】JSON文字列内でエラーを引き起こす特殊文字（バックスラッシュ、ダブルクォーテーション、改行文字）をエスケープ
     * @param value エスケープ対象の文字列
     * @return エスケープ処理済み文字列
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}