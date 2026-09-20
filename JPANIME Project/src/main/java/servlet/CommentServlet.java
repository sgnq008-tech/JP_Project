package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import common.DBConn;
import common.Json;

/**
 * 【サーブレット名】CommentServlet
 * 【URLマッピング】/api/comments
 * 【機能概要】
 *   1. GET : ?bno=X の全コメントを登録順に JSON 配列で返却
 *   2. POST: ログイン中ユーザーによるコメント登録 (bno, content)
 *   3. POST: action=delete&cno=Y で「自分のコメント」を削除 (v2 で追加)
 *
 * 【v2 の変更点】
 *   - コメント削除の追加 (WRITER = ログインユーザー の条件付き DELETE で他人のコメントは消せない)
 *   - 存在しないレビューへの投稿を 404 で拒否、コメント長(300文字)の検証
 *   - 例外メッセージ(e.getMessage())をレスポンスに含めない (内部情報の漏洩防止)
 *   - JSON 生成を Gson に統一
 */
@WebServlet("/api/comments")
public class CommentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final int MAX_COMMENT_LENGTH = 300;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        int bno;
        try {
            bno = Integer.parseInt(request.getParameter("bno").trim());
        } catch (Exception e) {
            Json.write(response, Collections.emptyList());
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                Json.write(response, Collections.emptyList());
                return;
            }

            pstmt = conn.prepareStatement(
                    "SELECT CNO, BNO, WRITER, CONTENT, TO_CHAR(REG_DATE, 'YYYY-MM-DD HH24:MI') AS REG_DATE_STR " +
                    "FROM REVIEW_COMMENTS WHERE BNO = ? ORDER BY CNO ASC");
            pstmt.setInt(1, bno);
            rs = pstmt.executeQuery();

            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cno", rs.getInt("CNO"));
                m.put("bno", rs.getInt("BNO"));
                m.put("writer", rs.getString("WRITER"));
                m.put("content", rs.getString("CONTENT"));
                m.put("regDate", rs.getString("REG_DATE_STR"));
                list.add(m);
            }
            Json.write(response, list);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Json.write(response, Collections.emptyList());
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        // ログイン確認 (未ログインは 401)
        HttpSession session = request.getSession(false);
        String loginId = (session != null) ? (String) session.getAttribute("loginId") : null;
        if (loginId == null || loginId.trim().isEmpty()) {
            fail(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED");
            return;
        }

        if ("delete".equals(request.getParameter("action"))) {
            deleteComment(request, response, loginId.trim());
        } else {
            insertComment(request, response, loginId.trim());
        }
    }

    private void insertComment(HttpServletRequest request, HttpServletResponse response, String loginId)
            throws IOException {
        int bno;
        try {
            bno = Integer.parseInt(request.getParameter("bno").trim());
        } catch (Exception e) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST");
            return;
        }
        String content = request.getParameter("content");
        if (content == null || content.trim().isEmpty() || content.trim().length() > MAX_COMMENT_LENGTH) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                fail(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR");
                return;
            }

            // 対象レビューの存在確認
            pstmt = conn.prepareStatement("SELECT 1 FROM ANIME_REVIEWS WHERE BNO = ?");
            pstmt.setInt(1, bno);
            rs = pstmt.executeQuery();
            boolean exists = rs.next();
            DBConn.close(rs, pstmt);
            if (!exists) {
                fail(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND");
                return;
            }

            pstmt = conn.prepareStatement(
                    "INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE) " +
                    "VALUES (SEQ_COMMENT_CNO.NEXTVAL, ?, ?, ?, SYSDATE)");
            pstmt.setInt(1, bno);
            pstmt.setString(2, loginId);
            pstmt.setString(3, content.trim());

            if (pstmt.executeUpdate() > 0) {
                ok(response);
            } else {
                fail(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR");
        } finally {
            DBConn.close(rs, pstmt, conn);
        }
    }

    /** 自分が書いたコメントのみ削除できる (WRITER 条件付き DELETE)。 */
    private void deleteComment(HttpServletRequest request, HttpServletResponse response, String loginId)
            throws IOException {
        int cno;
        try {
            cno = Integer.parseInt(request.getParameter("cno").trim());
        } catch (Exception e) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST");
            return;
        }

        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DBConn.getConnection();
            if (conn == null) {
                fail(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR");
                return;
            }
            pstmt = conn.prepareStatement("DELETE FROM REVIEW_COMMENTS WHERE CNO = ? AND WRITER = ?");
            pstmt.setInt(1, cno);
            pstmt.setString(2, loginId);

            if (pstmt.executeUpdate() > 0) {
                ok(response);
            } else {
                // 存在しない or 他人のコメント
                fail(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR");
        } finally {
            DBConn.close(pstmt, conn);
        }
    }

    private void ok(HttpServletResponse response) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        Json.write(response, m);
    }

    private void fail(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        Json.write(response, m);
    }
}
