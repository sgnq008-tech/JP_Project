package common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.ReviewIntelligence.AnalysisResult;
import org.junit.Test;

public class ReviewIntelligenceTest {

    @Test
    public void emptyInputIsNeutralWithLowConfidence() {
        AnalysisResult r = ReviewIntelligence.analyze("", "", "en");
        assertEquals(3, r.recommendedRating);
        assertEquals("50%", r.sentimentScore);
    }

    @Test
    public void negationFlipsEnglishNegativeKeyword() {
        // "not bad" は肯定として数える → 3 + 1 = 4
        assertEquals(4, ReviewIntelligence.analyze("", "Not bad at all", "en").recommendedRating);
    }

    @Test
    public void negationFlipsEnglishPositiveKeyword() {
        // "not great" は否定として数える → 3 - 1 = 2
        assertEquals(2, ReviewIntelligence.analyze("", "The ending was not great", "en").recommendedRating);
    }

    @Test
    public void plainNegativeKeywordLowersRating() {
        assertEquals(2, ReviewIntelligence.analyze("", "This anime is bad", "en").recommendedRating);
    }

    @Test
    public void englishKeywordsMatchWholeWordsOnly() {
        // "badge" の中の "bad" に反応しない
        assertEquals(3, ReviewIntelligence.analyze("", "I love the badge design", "en").recommendedRating);
    }

    @Test
    public void japaneseKeywordsRaiseRatingAndClampAtFive() {
        // 「最高」「神作」「名作」「鳥肌」... 3 + 4 → 上限5
        AnalysisResult r = ReviewIntelligence.analyze("最高の名作", "神作画で鳥肌が立った", "ja");
        assertEquals(5, r.recommendedRating);
    }

    @Test
    public void ratingNeverDropsBelowOne() {
        AnalysisResult r = ReviewIntelligence.analyze("最悪", "つまらない 退屈 期待外れ 残念 boring worst", "ja");
        assertEquals(1, r.recommendedRating);
    }

    @Test
    public void sfTagDoesNotFireInsideOtherWords() {
        // "transformers" に含まれる "sf" で SF タグが付かない (デフォルトタグになる)
        AnalysisResult r = ReviewIntelligence.analyze("", "transformers is fun", "en");
        assertFalse(r.extractedTags.contains("#SF・メカニック"));
        assertTrue(r.extractedTags.contains("#AnimeReview"));
    }

    @Test
    public void koreanPhilosophyKeywordTriggersMechaTag() {
        // 辞書の誤字 "철学" を修正した回帰テスト
        AnalysisResult r = ReviewIntelligence.analyze("", "정치와 철학이 인상적", "ko");
        assertTrue(r.extractedTags.contains("#SF・メカニック"));
    }

    @Test
    public void defaultTagFollowsLanguage() {
        assertTrue(ReviewIntelligence.analyze("", "hello", "ko").extractedTags.contains("#애니감상"));
        assertTrue(ReviewIntelligence.analyze("", "hello", "en").extractedTags.contains("#AnimeReview"));
        assertTrue(ReviewIntelligence.analyze("", "hello", "ja").extractedTags.contains("#アニメ感想"));
    }

    @Test
    public void jsonUsesTagsKeyExpectedByFrontend() {
        String json = ReviewIntelligence.analyze("", "great action", "en").toJson();
        assertTrue(json.contains("\"tags\""));
        assertTrue(json.contains("\"recommendedRating\""));
    }
}
