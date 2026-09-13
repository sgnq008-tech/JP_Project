package common;

import java.util.ArrayList;
import java.util.List;

/**
 * AniLog AI レビュー知能型テキスト分析エンジン (Review Intelligence Engine)
 * 自然言語の感情分析、スマート評価点算出、タグ自動抽出を担当
 */
public class ReviewIntelligence {

    // 感情・品質ポジティブキーワード辞書
    private static final String[] POSITIVE_KEYWORDS = {
            "최고", "압도", "명작", "추천", "감동", "미쳤", "완벽", "대단", "꿀잼", "레전드", "소름", "훌륭", "갓작",
            "最高", "圧巻", "名作", "おすすめ", "感動", "完璧", "素晴らしい", "神作", "鳥肌", "圧倒", "神アニメ", "良作",
            "masterpiece", "amazing", "perfect", "great", "awesome", "incredible", "legendary", "best", "superb"
    };

    // ネガティブキーワード辞書
    private static final String[] NEGATIVE_KEYWORDS = {
            "아쉽", "지루", "실망", "노잼", "별로", "작붕", "최악", "낭비", "부족", "답답",
            "微妙", "残念", "退屈", "期待外れ", "作画崩壊", "最悪", "物足りない", "つまらない", "不満",
            "disappointing", "boring", "bad", "worst", "poor", "waste", "mediocre", "dull"
    };

    // ジャンル別キーワードマッピング辞書
    private static final String[][] TAG_RULES = {
            {"#バトルアクション", "전투", "액션", "작화", "카메라", "전쟁", "戦闘", "バトル", "アクション", "作画", "action", "battle", "fight"},
            {"#感動・名作", "눈물", "감동", "성장", "희망", "눈물샘", "感動", "涙", "泣ける", "成長", "emotional", "touching", "tears"},
            {"#ダークファンタジー", "어두", "주술", "악마", "사변", "절망", "呪術", "ダーク", "絶望", "悪魔", "dark", "sorcery", "demons"},
            {"#SF・メカニック", "로봇", "건담", "메카", "우주", "정치", "철学", "ガンダム", "メカ", "SF", "ロボット", "宇宙", "mecha", "gundam", "scifi"},
            {"#日常・コメディ", "일상", "개그", "웃김", "유쾌", "치유", "日常", "ギャグ", "コメディ", "面白い", "癒し", "comedy", "funny", "slice of life"}
    };

    // 分析結果を保持する内部クラス
    public static class AnalysisResult {
        public int recommendedRating;       // 推奨星評価 (1~5)
        public String sentimentLabel;       // 感情分析ラベル
        public String sentimentScore;       // 感情スコア (0~100%)
        public List<String> extractedTags;  // 自動抽出されたスマートタグ
        public String aiSummary;            // AIによる自動サマリー評価

        public AnalysisResult() {
            this.extractedTags = new ArrayList<>();
        }

        // サーブレット応答用のJSONシリアライズ処理
        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"recommendedRating\":").append(recommendedRating).append(",");
            sb.append("\"sentimentLabel\":\"").append(escape(sentimentLabel)).append("\",");
            sb.append("\"sentimentScore\":\"").append(escape(sentimentScore)).append("\",");
            sb.append("\"aiSummary\":\"").append(escape(aiSummary)).append("\",");
            sb.append("\"tags\":[");
            for (int i = 0; i < extractedTags.size(); i++) {
                sb.append("\"").append(escape(extractedTags.get(i))).append("\"");
                if (i < extractedTags.size() - 1) sb.append(",");
            }
            sb.append("]}");
            return sb.toString();
        }

        private String escape(String val) {
            if (val == null) return "";
            return val.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        }
    }

    /**
     * デフォルト言語(ja)による総合分析
     */
    public static AnalysisResult analyze(String title, String content) {
        return analyze(title, content, "ja");
    }

    /**
     * 言語指定(ja, ko, en)によるレビュー分析
     */
    public static AnalysisResult analyze(String title, String content, String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            lang = "ja";
        }

        AnalysisResult result = new AnalysisResult();
        String combined = ((title != null ? title : "") + " " + (content != null ? content : "")).toLowerCase();

        int posCount = 0;
        int negCount = 0;

        for (String pos : POSITIVE_KEYWORDS) {
            if (combined.contains(pos.toLowerCase())) posCount++;
        }

        for (String neg : NEGATIVE_KEYWORDS) {
            if (combined.contains(neg.toLowerCase())) negCount++;
        }

        // 星評価インテリジェンス算出 (基本3点から加減算)
        int calculatedRating = 3 + posCount - negCount;
        if (calculatedRating > 5) calculatedRating = 5;
        if (calculatedRating < 1) calculatedRating = 1;
        result.recommendedRating = calculatedRating;

        // 信頼度スコアの判定 (基本60% + 検知シグナル数*8%)
        int totalSignals = posCount + negCount;
        int confidence = (totalSignals > 0) ? Math.min(60 + (totalSignals * 8), 98) : 50;
        result.sentimentScore = confidence + "%";

        // 多言語別ラベリングと要約の生成
        applyI18nLabels(result, calculatedRating, lang);

        // タグ自動抽出インテリジェンス
        for (String[] rule : TAG_RULES) {
            String tagName = rule[0];
            for (int i = 1; i < rule.length; i++) {
                if (combined.contains(rule[i].toLowerCase())) {
                    if (!result.extractedTags.contains(tagName)) {
                        result.extractedTags.add(tagName);
                    }
                    break;
                }
            }
        }

        // デフォルトタグの付与
        if (result.extractedTags.isEmpty()) {
            if ("ko".equals(lang)) {
                result.extractedTags.add("#애니감상");
            } else if ("en".equals(lang)) {
                result.extractedTags.add("#AnimeReview");
            } else {
                result.extractedTags.add("#アニメ感想");
            }
        }

        return result;
    }

    // 多言語辞書適用
    private static void applyI18nLabels(AnalysisResult res, int rating, String lang) {
        if ("ko".equals(lang)) {
            if (rating == 5) {
                res.sentimentLabel = "압도적 긍정 (Masterpiece / 대절찬)";
                res.aiSummary = "작화, 연출, 스토리 완성도 전반에 걸쳐 극찬과 매우 높은 만족도가 감지되었습니다.";
            } else if (rating == 4) {
                res.sentimentLabel = "긍정적 (Recommended / 호평)";
                res.aiSummary = "전체적인 완성도가 우수하며 동 장르 팬들에게 충분히 추천할 만한 작품입니다.";
            } else if (rating == 3) {
                res.sentimentLabel = "중립적/평이함 (Neutral / 보통)";
                res.aiSummary = "작품의 매력적인 요소와 아쉬운 점이 공존하는 균형 잡힌 감상평입니다.";
            } else {
                res.sentimentLabel = "부정적 (Critical / 아쉬움)";
                res.aiSummary = "작화 퀄리티나 스토리 전개 속도, 개연성 측면에서 불만족 신호가 감지되었습니다.";
            }
        } else if ("en".equals(lang)) {
            if (rating == 5) {
                res.sentimentLabel = "Overwhelmingly Positive (Masterpiece)";
                res.aiSummary = "Detected extremely high satisfaction with visuals, pacing, and emotional depth.";
            } else if (rating == 4) {
                res.sentimentLabel = "Positive (Recommended)";
                res.aiSummary = "Solid execution overall, recommended for fans of the genre.";
            } else if (rating == 3) {
                res.sentimentLabel = "Neutral (Average)";
                res.aiSummary = "Balanced feedback mentioning both strong points and shortcomings.";
            } else {
                res.sentimentLabel = "Critical (Needs Improvement)";
                res.aiSummary = "Identified concerns regarding animation quality, storytelling, or pacing.";
            }
        } else {
            // Default: 日本語
            if (rating == 5) {
                res.sentimentLabel = "極めて肯定的 (Masterpiece / 大絶賛)";
                res.aiSummary = "圧倒的な作画・演出またはストーリー性に対して非常に高い満足度が検知されました。";
            } else if (rating == 4) {
                res.sentimentLabel = "肯定的 (Recommended / 好評)";
                res.aiSummary = "全体的に完成度が高く、ファン層におすすめできる優良作として評価されています。";
            } else if (rating == 3) {
                res.sentimentLabel = "中立的・平均的 (Neutral / 普通)";
                res.aiSummary = "長所と惜しい点が共存しているバランス型のレビューです。";
            } else {
                res.sentimentLabel = "否定的 (Critical / 批判的)";
                res.aiSummary = "作画クオリティやストーリー展開のテンポに関して不満・改善要求が検出されました。";
            }
        }
    }
}