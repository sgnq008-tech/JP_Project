// 多言語辞書オブジェクト (日本語 / 韓国語 / 英語)
const i18n = {
    ja: {
        // 共通・ブランド・ナビゲーション
        siteBrand: "🎬 アニログ (AniLog)",
        welcomeSuffix: " 様、ようこそ！",
        logoutBtn: "ログアウト",
        closeBtn: "閉じる",
        backToList: "一覧へ戻る",

        // ログイン・会員登録画面
        loginTitle: "ログイン",
        loginBtn: "ログイン",
        joinTitle: "会員登録",
        joinBtn: "新規会員登録",
        joinCompleteBtn: "登録完了",
        userId: "ユーザーID",
        userPw: "パスワード",
        userPwConfirm: "パスワードの再確認",
        userName: "お名前",
        hasAccount: "既にアカウントをお持ちですか？",
        noAccount: "アカウントをお持ちでないですか？",
        goToLogin: "ログインはこちら",

        // プレースホルダー (認証系)
        idPlaceholder: "英数字4〜20文字",
        pwPlaceholder: "4文字以上",
        pwConfirmPlaceholder: "もう一度入力",
        namePlaceholder: "表示名を入力",

        // バリデーション & メッセージ (認証系)
        pwMatchSuccess: "✔ パスワードが一致しました",
        pwMatchError: "✖ パスワードが一致しません",
        pwAlertMismatch: "パスワードが一致していません。もう一度ご確認ください。",

        // メイン掲示板 (board.html)
        boardTitle: "🍿 アニメ感想・評価コミュニティ",
        newPostBtn: "レビュー作成",
        allPostsTab: "すべてのレビュー",
        myPostsTab: "自分のレビュー",
        noPosts: "該当するアニメレビューがありません。",
        noMyPosts: "作成したレビューがありません。",
        searchPlaceholder: "アニメ名やレビュータイトルで検索...",
        sortLatest: "最新順",
        sortRatingHigh: "星評価の高い順 (★5→1)",
        sortRatingLow: "星評価の低い順 (★1→5)",
        aiRankingTitle: "🤖 AI Pick: ユーザー高評価アニメ TOP 3",
        aiRecomTitle: "💡 AI 知能型レコメンド: この作品が好きな方へのおすすめ",

        // レビュー作成・編集画面 (write.html, edit.html)
        writeTitle: "アニメの感想・レビュー投稿",
        editTitle: "アニメレビューの編集",
        animeTitleLabel: "アニメ作品名",
        animePlaceholder: "例：僕のヒーローアカデミア、呪術廻戦、ガンダム",
        postTitleLabel: "レビュータイトル",
        postTitlePlaceholder: "レビューの要約やタイトルを入力",
        postContentLabel: "感想・レビュー本文",
        postContentPlaceholder: "アニメの感想や考察を自由に入力してください",
        ratingLabel: "評価（星評価）",
        imageLabel: "アニメ画像・ポスター添付",
        keepImageNotice: "※新しい画像を選択しない場合、既存の画像が維持されます。",
        saveBtn: "レビュー登録",
        updateBtn: "修正を保存",
        editBtn: "編集",
        deleteBtn: "削除",
        deleteConfirm: "このレビューを本当に削除しますか？",

        // AI インテリジェンス・アシスタント
        aiAssistantTitle: "🤖 AniLog AI レビュー感情分析＆星評価アシスタント",
        aiAnalyzeBtn: "AI自動分析",
        aiReAnalyzeBtn: "AI再分析",
        aiSentimentLabel: "感情判定:",
        aiSummaryLabel: "AI要約:",
        aiTagsLabel: "自動抽出タグ:",

        // コメント機能
        commentTitle: "💬 コメント",
        commentPlaceholder: "一言感想を残してください...",
        commentSubmitBtn: "登録",
        noComments: "まだコメントがありません。最初のコメントを残してみましょう！",

        // 星評価選択肢
        rating5: "★★★★★ (5点 - 最高)",
        rating4: "★★★★☆ (4点 - 良い)",
        rating3: "★★★☆☆ (3点 - 普通)",
        rating2: "★★☆☆☆ (2点 - 微妙)",
        rating1: "★☆☆☆☆ (1点 - 不満)"
    },
    ko: {
        // 共通・ブランド・ナビゲーション
        siteBrand: "🎬 애니로그 (AniLog)",
        welcomeSuffix: "님 환영합니다!",
        logoutBtn: "로그아웃",
        closeBtn: "닫기",
        backToList: "목록으로",

        // ログイン・会員登録画面
        loginTitle: "로그인",
        loginBtn: "로그인",
        joinTitle: "회원가입",
        joinBtn: "회원가입",
        joinCompleteBtn: "가입완료",
        userId: "아이디",
        userPw: "비밀번호",
        userPwConfirm: "비밀번호 재확인",
        userName: "이름",
        hasAccount: "이미 계정이 있으신가요?",
        noAccount: "계정이 없으신가요?",
        goToLogin: "로그인하러 가기",

        // プレースホルダー (認証系)
        idPlaceholder: "영문/숫자 4~20자",
        pwPlaceholder: "4자 이상 입력",
        pwConfirmPlaceholder: "비밀번호 다시 입력",
        namePlaceholder: "이름(닉네임) 입력",

        // バリデーション & メッセージ (認証系)
        pwMatchSuccess: "✔ 비밀번호가 일치합니다",
        pwMatchError: "✖ 비밀번호가 일치하지 않습니다",
        pwAlertMismatch: "비밀번호가 서로 일치하지 않습니다. 다시 확인해 주세요.",

        // メイン掲示板 (board.html)
        boardTitle: "🍿 애니 감상평 & 리뷰 커뮤니티",
        newPostBtn: "리뷰 작성",
        allPostsTab: "전체 리뷰",
        myPostsTab: "내가 쓴 리뷰",
        noPosts: "해당하는 애니메이션 리뷰가 없습니다.",
        noMyPosts: "내가 작성한 리뷰가 없습니다.",
        searchPlaceholder: "애니 제목 또는 리뷰 키워드로 검색...",
        sortLatest: "최신 등록순",
        sortRatingHigh: "별점 높은 순 (★5→1)",
        sortRatingLow: "별점 낮은 순 (★1→5)",
        aiRankingTitle: "🤖 AI Pick: 유저 최고 평점 애니 TOP 3",
        aiRecomTitle: "💡 AI 지능형 추천: 이 작품을 좋아하신다면",

        // レビュー作成・編集画面 (write.html, edit.html)
        writeTitle: "애니메이션 감상평 남기기",
        editTitle: "애니메이션 리뷰 수정하기",
        animeTitleLabel: "애니메이션 제목",
        animePlaceholder: "예: 나의 히어로 아카데미아, 주술회전, 건담",
        postTitleLabel: "리뷰 제목",
        postTitlePlaceholder: "리뷰 요약이나 한 줄 평가를 입력하세요",
        postContentLabel: "감상 후기 및 의견",
        postContentPlaceholder: "애니메이션 감상평이나 의견을 자유롭게 작성해 주세요",
        ratingLabel: "평점 (별점)",
        imageLabel: "애니메이션 포스터/이미지 첨부",
        keepImageNotice: "※새 이미지를 선택하지 않으면 기존 이미지가 그대로 유지됩니다.",
        saveBtn: "리뷰 등록",
        updateBtn: "수정 완료",
        editBtn: "수정하기",
        deleteBtn: "삭제하기",
        deleteConfirm: "이 리뷰를 정말 삭제하시겠습니까?",

        // AI インテリジェンス・アシスタント
        aiAssistantTitle: "🤖 AniLog AI 리뷰 감성 분석 & 별점 어시스턴트",
        aiAnalyzeBtn: "AI 자동 분석",
        aiReAnalyzeBtn: "AI 재분석",
        aiSentimentLabel: "감성 판별:",
        aiSummaryLabel: "AI 요약:",
        aiTagsLabel: "자동 추출 태그:",

        // コメント機能
        commentTitle: "💬 한 줄 댓글",
        commentPlaceholder: "한 줄 감상평을 남겨보세요...",
        commentSubmitBtn: "댓글 등록",
        noComments: "아직 작성된 댓글이 없습니다. 첫 댓글을 남겨보세요!",

        // 星評価選択肢
        rating5: "★★★★★ (5점 - 최고)",
        rating4: "★★★★☆ (4점 - 추천)",
        rating3: "★★★☆☆ (3점 - 보통)",
        rating2: "★★☆☆☆ (2점 - 아쉬움)",
        rating1: "★☆☆☆☆ (1점 - 비추천)"
    },
    en: {
        // 共通・ブランド・ナビゲーション
        siteBrand: "🎬 AniLog",
        welcomeSuffix: ", welcome!",
        logoutBtn: "Sign Out",
        closeBtn: "Close",
        backToList: "Back to List",

        // ログイン・会員登録画面
        loginTitle: "Sign In",
        loginBtn: "Sign In",
        joinTitle: "Sign Up",
        joinBtn: "Create Account",
        joinCompleteBtn: "Register",
        userId: "Username",
        userPw: "Password",
        userPwConfirm: "Confirm Password",
        userName: "Full Name",
        hasAccount: "Already have an account?",
        noAccount: "Don't have an account?",
        goToLogin: "Sign In here",

        // プレースホルダー (認証系)
        idPlaceholder: "4-20 alphanumeric",
        pwPlaceholder: "4 characters min",
        pwConfirmPlaceholder: "Re-enter password",
        namePlaceholder: "Enter display name",

        // バリデーション & メッセージ (認証系)
        pwMatchSuccess: "✔ Passwords match",
        pwMatchError: "✖ Passwords do not match",
        pwAlertMismatch: "Passwords do not match. Please verify again.",

        // メイン掲示板 (board.html)
        boardTitle: "🍿 Anime Reviews & Ratings Community",
        newPostBtn: "Write Review",
        allPostsTab: "All Reviews",
        myPostsTab: "My Reviews",
        noPosts: "No anime reviews match your query.",
        noMyPosts: "You haven't written any reviews yet.",
        searchPlaceholder: "Search by anime or review title...",
        sortLatest: "Latest",
        sortRatingHigh: "Highest Rating (★5→1)",
        sortRatingLow: "Lowest Rating (★1→5)",
        aiRankingTitle: "🤖 AI Pick: Top Rated Anime TOP 3",
        aiRecomTitle: "💡 AI Recommendations: If you liked this anime",

        // レビュー作成・編集画面 (write.html, edit.html)
        writeTitle: "Leave an Anime Review",
        editTitle: "Edit Anime Review",
        animeTitleLabel: "Anime Title",
        animePlaceholder: "e.g., My Hero Academia, Jujutsu Kaisen, Gundam",
        postTitleLabel: "Review Title",
        postTitlePlaceholder: "Enter a brief summary or title for your review",
        postContentLabel: "Thoughts & Feedback",
        postContentPlaceholder: "Share your honest impressions and opinions about the anime",
        ratingLabel: "Rating",
        imageLabel: "Anime Poster / Image Upload",
        keepImageNotice: "※If no new image is selected, the current image will be kept.",
        saveBtn: "Post Review",
        updateBtn: "Update Review",
        editBtn: "Edit",
        deleteBtn: "Delete",
        deleteConfirm: "Are you sure you want to delete this review?",

        // AI インテリジェンス・アシスタント
        aiAssistantTitle: "🤖 AniLog AI Sentiment & Rating Assistant",
        aiAnalyzeBtn: "AI Auto Analyze",
        aiReAnalyzeBtn: "AI Re-analyze",
        aiSentimentLabel: "Sentiment:",
        aiSummaryLabel: "AI Summary:",
        aiTagsLabel: "Extracted Tags:",

        // コメント機能
        commentTitle: "💬 Comments",
        commentPlaceholder: "Write a short comment...",
        commentSubmitBtn: "Post",
        noComments: "No comments yet. Be the first to comment!",

        // 星評価選択肢
        rating5: "★★★★★ (5 Stars - Masterpiece)",
        rating4: "★★★★☆ (4 Stars - Great)",
        rating3: "★★★☆☆ (3 Stars - Average)",
        rating2: "★★☆☆☆ (2 Stars - Mediocre)",
        rating1: "★☆☆☆☆ (1 Star - Poor)"
    }
};

// 選択中の言語コード保持（ローカルストレージから復元、デフォルト: 日本語）
let currentLang = localStorage.getItem("siteLang") || "ja";

// DOM要素全体への多言語辞書データ適用処理
function applyLanguage(lang) {
    currentLang = lang;
    localStorage.setItem("siteLang", lang);

    // テキスト要素の書き換え (data-i18n)
    document.querySelectorAll("[data-i18n]").forEach(el => {
        const key = el.getAttribute("data-i18n");
        if (i18n[lang] && i18n[lang][key]) {
            el.innerText = i18n[lang][key];
        }
    });

    // プレースホルダーの書き換え (data-i18n-placeholder)
    document.querySelectorAll("[data-i18n-placeholder]").forEach(el => {
        const key = el.getAttribute("data-i18n-placeholder");
        if (i18n[lang] && i18n[lang][key]) {
            el.placeholder = i18n[lang][key];
        }
    });

    // ドロップダウンリストの項目名書き換え (data-i18n-opt)
    document.querySelectorAll("[data-i18n-opt]").forEach(el => {
        const key = el.getAttribute("data-i18n-opt");
        if (i18n[lang] && i18n[lang][key]) {
            el.innerText = i18n[lang][key];
        }
    });

    // 言語切り替えボタンのアクティブ状態の制御
    const btnJa = document.getElementById("btnJa");
    const btnKo = document.getElementById("btnKo");
    const btnEn = document.getElementById("btnEn");
    if (btnJa) btnJa.classList.toggle("active", lang === "ja");
    if (btnKo) btnKo.classList.toggle("active", lang === "ko");
    if (btnEn) btnEn.classList.toggle("active", lang === "en");
}

// 外部呼び出し用言語変更関数
function changeLanguage(lang) {
    applyLanguage(lang);
}

// ドキュメントロード完了時の初期適用
document.addEventListener("DOMContentLoaded", () => {
    applyLanguage(currentLang);
});