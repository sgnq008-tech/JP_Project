package servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import common.ReviewIntelligence;
import common.ReviewIntelligence.AnalysisResult;

/**
 * 【サーブレット名】IntelligenceServlet
 * 【URLマッピング】/api/intelligence
 * 【機能概要】
 *   投稿テキスト(title, content)のルールベース感情分析・タグ抽出結果を JSON で返す。
 *   lang パラメータ(ja / ko / en)で結果ラベルの言語を切り替える。
 *
 * 【v2 の変更点】
 *   - lang パラメータを読み取るように修正 (v1 は常に日本語で返していた)
 *   - 入力長を制限 (過大なテキストによる無駄な処理を防止)
 */
@WebServlet("/api/intelligence")
public class IntelligenceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final int MAX_INPUT_LENGTH = 5000;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        String title = limit(request.getParameter("title"));
        String content = limit(request.getParameter("content"));
        String lang = request.getParameter("lang");
        if (!"ko".equals(lang) && !"en".equals(lang)) {
            lang = "ja"; // 未指定・不正値は日本語
        }

        AnalysisResult analysis = ReviewIntelligence.analyze(title, content, lang);
        response.getWriter().write(analysis.toJson());
    }

    private static String limit(String s) {
        if (s == null) return "";
        return s.length() > MAX_INPUT_LENGTH ? s.substring(0, MAX_INPUT_LENGTH) : s;
    }
}
