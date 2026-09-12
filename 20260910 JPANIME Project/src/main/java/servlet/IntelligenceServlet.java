package servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import common.ReviewIntelligence;
import common.ReviewIntelligence.AnalysisResult;

@WebServlet("/api/intelligence")
public class IntelligenceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // POST: 投稿テキストのAIリアルタイム自然言語解析
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        String title = request.getParameter("title");
        String content = request.getParameter("content");

        // AI 知能型分析の実行
        AnalysisResult analysis = ReviewIntelligence.analyze(title, content);

        StringBuilder json = new StringBuilder("{");
        json.append("\"recommendedRating\":").append(analysis.recommendedRating).append(",")
                .append("\"sentimentLabel\":\"").append(escapeJson(analysis.sentimentLabel)).append("\",")
                .append("\"sentimentScore\":\"").append(escapeJson(analysis.sentimentScore)).append("\",")
                .append("\"aiSummary\":\"").append(escapeJson(analysis.aiSummary)).append("\",")
                .append("\"tags\":[");

        for (int i = 0; i < analysis.extractedTags.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(analysis.extractedTags.get(i))).append("\"");
        }
        json.append("]}");

        response.getWriter().write(json.toString());
    }

    private String escapeJson(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}