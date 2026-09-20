package common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AniLog レビュー分析エンジン (ルールベース)
 *
 * 【方式】
 *   機械学習ではなく「キーワード辞書 + ルール」による感情分析・タグ抽出。
 *   辞書のキーワードが本文に含まれる数から星評価を算出する (基本3点、肯定+1 / 否定-1)。
 *
 * 【v2 での改善】
 *   1. 英語キーワードは単語境界で判定 (例: "bad" が "badge" に誤反応しない / "sf" が "transformers" に反応しない)
 *   2. 英語の否定表現を考慮 (例: "not bad" は肯定、"not great" は否定として数える)
 *   3. タグ辞書内の誤字を修正 ("철学" → "철학")
 *
 * 【既知の限界】
 *   - 日本語・韓国語は部分一致のみで、否定形(例: 「つまらなくない」)は判定できない。
 *     形態素解析(Kuromoji 等)や LLM API への置き換えが次のステップ。
 *   - 皮肉・文脈依存の表現は判定できない。
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

    // ジャンル別キーワードマッピング辞書 (先頭要素がタグ名、以降がキーワード)
    private static final String[][] TAG_RULES = {
            {"#バトルアクション", "전투", "액션", "작화", "카메라", "전쟁", "戦闘", "バトル", "アクション", "作画", "action", "battle", "fight"},
            {"#感動・名作", "눈물", "감동", "성장", "희망", "눈물샘", "感動", "涙", "泣ける", "成長", "emotional", "touching", "tears"},
            {"#ダークファンタジー", "어두", "주술", "악마", "사변", "절망", "呪術", "ダーク", "絶望", "悪魔", "dark", "sorcery", "demons"},
            {"#SF・メカニック", "로봇", "건담", "메카", "우주", "정치", "철학", "ガンダム", "メカ", "SF", "ロボット", "宇宙", "mecha", "gundam", "scifi"},
            {"#日常・コメディ", "일상", "개그", "웃김", "유쾌", "치유", "日常", "ギャグ", "コメディ", "面白い", "癒し", "comedy", "funny", "slice of life"}
    };

    // 英語の否定語 (直前に付くと極性が反転する)
    private static final String NEGATORS =
            "(?:not|never|no|isn't|wasn't|aren't|weren't|don't|doesn't|didn't|hardly)";
    // 否定語と本文キーワードの間に入り得る強調語・冠詞
    private static final String FILLERS = "(?:(?:very|that|so|really|too|the|a)\\s+)?";

    // 英語キーワード用の正規表現キャッシュ (キーワード → Pattern)
    private static final Map<String, Pattern> SENTIMENT_PATTERNS = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> WORD_PATTERNS = new ConcurrentHashMap<>();

    // 分析結果を保持する内部クラス
    public static class AnalysisResult {
        public int recommendedRating;       // 推奨星評価 (1~5)
        public String sentimentLabel;       // 感情分析ラベル
        public String sentimentScore;       // 信頼度スコア (%)
        public List<String> extractedTags;  // 自動抽出されたタグ
        public String aiSummary;            // 自動サマリー

        public AnalysisResult() {
            this.extractedTags = new ArrayList<>();
        }

        // サーブレット応答用のJSONシリアライズ処理
        // (フロントエンドが参照するキー名 "tags" に合わせるため Map 経由で出力)
        public String toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recommendedRating", recommendedRating);
            m.put("sentimentLabel", sentimentLabel);
            m.put("sentimentScore", sentimentScore);
            m.put("aiSummary", aiSummary);
            m.put("tags", extractedTags);
            return Json.toJson(m);
        }
    }

    /** デフォルト言語(ja)による総合分析 */
    public static AnalysisResult analyze(String title, String content) {
        return analyze(title, content, "ja");
    }

    /** 言語指定(ja, ko, en)によるレビュー分析 */
    public static AnalysisResult analyze(String title, String content, String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            lang = "ja";
        }

        AnalysisResult result = new AnalysisResult();
        String combined = ((title != null ? title : "") + " " + (content != null ? content : ""))
                .toLowerCase(Locale.ROOT);

        int posCount = 0;
        int negCount = 0;

        // 肯定語: 否定表現が付いていれば否定として数える
        for (String kw : POSITIVE_KEYWORDS) {
            int polarity = sentimentPolarity(combined, kw);
            if (polarity > 0) posCount++;
            else if (polarity < 0) negCount++;
        }
        // 否定語: 否定表現が付いていれば肯定として数える (例: "not bad")
        for (String kw : NEGATIVE_KEYWORDS) {
            int polarity = sentimentPolarity(combined, kw);
            if (polarity > 0) negCount++;
            else if (polarity < 0) posCount++;
        }

        // 星評価 (基本3点から加減算し 1~5 に丸める)
        int calculatedRating = 3 + posCount - negCount;
        if (calculatedRating > 5) calculatedRating = 5;
        if (calculatedRating < 1) calculatedRating = 1;
        result.recommendedRating = calculatedRating;

        // 信頼度スコア (基本60% + 検知シグナル数×8%、上限98%。シグナル無しは50%)
        int totalSignals = posCount + negCount;
        int confidence = (totalSignals > 0) ? Math.min(60 + (totalSignals * 8), 98) : 50;
        result.sentimentScore = confidence + "%";

        applyI18nLabels(result, calculatedRating, lang);

        // タグ抽出
        for (String[] rule : TAG_RULES) {
            String tagName = rule[0];
            for (int i = 1; i < rule.length; i++) {
                if (containsKeyword(combined, rule[i].toLowerCase(Locale.ROOT))) {
                    if (!result.extractedTags.contains(tagName)) {
                        result.extractedTags.add(tagName);
                    }
                    break;
                }
            }
        }

        // デフォルトタグ
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

    // ---------------------------------------------------------------------
    //  キーワード判定
    // ---------------------------------------------------------------------

    /**
     * キーワードの出現と極性を返す。
     *   +1 : 出現し、否定されていない
     *   -1 : 出現し、直前に否定語が付いている (英語のみ)
     *    0 : 出現しない
     * 日本語・韓国語などASCII以外のキーワードは単純な部分一致 (否定判定なし)。
     */
    static int sentimentPolarity(String lowerText, String keyword) {
        String kw = keyword.toLowerCase(Locale.ROOT);
        if (!isAscii(kw)) {
            return lowerText.contains(kw) ? 1 : 0;
        }
        Matcher m = SENTIMENT_PATTERNS.computeIfAbsent(kw, k -> Pattern.compile(
                "(?<![a-z0-9])(?:(" + NEGATORS + ")\\s+" + FILLERS + ")?" + Pattern.quote(k) + "(?![a-z0-9])"))
                .matcher(lowerText);
        if (m.find()) {
            return (m.group(1) != null) ? -1 : 1;
        }
        return 0;
    }

    /** タグ判定用: ASCIIキーワードは単語境界、それ以外は部分一致。 */
    static boolean containsKeyword(String lowerText, String lowerKeyword) {
        if (!isAscii(lowerKeyword)) {
            return lowerText.contains(lowerKeyword);
        }
        return WORD_PATTERNS.computeIfAbsent(lowerKeyword, k -> Pattern.compile(
                "(?<![a-z0-9])" + Pattern.quote(k) + "(?![a-z0-9])"))
                .matcher(lowerText).find();
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------
    //  多言語ラベル
    // ---------------------------------------------------------------------

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
