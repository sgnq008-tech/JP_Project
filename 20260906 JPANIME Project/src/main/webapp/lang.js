// 多言語辞書オブジェクト (日本語 / 韓国語 / 英語)
const i18n = {
    ja: {
        siteBrand: "🎬 アニログ (AniLog)",
        loginTitle: "ログイン",
        userId: "ユーザーID",
        userPw: "パスワード",
        userName: "お名前",
        loginBtn: "ログイン",
        joinBtn: "新規会員登録",
        joinTitle: "会員登録",
        joinCompleteBtn: "登録完了",
        boardTitle: "🍿 アニメ感想・評価コミュニティ",
        logoutBtn: "ログアウト",
        newPostBtn: "レビュー作成",
        allPostsTab: "すべてのレビュー",
        myPostsTab: "自分のレビュー",
        noPosts: "該当するアニメレビューがありません。",
        noMyPosts: "作成したレビューがありません。",
        welcomeSuffix: " 様、ようこそ！",
        writeTitle: "アニメの感想・レビュー投稿",
        editTitle: "アニメレビューの編集",
        animeTitleLabel: "アニメ作品名",
        ratingLabel: "評価（星評価）",
        imageLabel: "アニメ画像・ポスター添付",
        keepImageNotice: "※新しい画像を選択しない場合、既存の画像が維持されます。",
        postTitleLabel: "レビュータイトル",
        postContentLabel: "感想・レビュー本文",
        saveBtn: "レビュー登録",
        updateBtn: "修正を保存",
        editBtn: "編集",
        deleteBtn: "削除",
        deleteConfirm: "このレビューを本当に削除しますか？",
        closeBtn: "閉じる",
        backToList: "一覧へ戻る",
        searchPlaceholder: "アニメ名やレビュータイトルで検索...",
        sortLatest: "最新順",
        sortRatingHigh: "星評価の高い順 (★5→1)",
        sortRatingLow: "星評価の低い順 (★1→5)",
        commentTitle: "💬 コメント",
        commentPlaceholder: "一言感想を残してください...",
        commentSubmitBtn: "登録",
        noComments: "まだコメントがありません。最初のコメントを残してみましょう！",
        rating5: "★★★★★ (5点 - 最高)",
        rating4: "★★★★☆ (4点 - 良い)",
        rating3: "★★★☆☆ (3点 - 普通)",
        rating2: "★★☆☆☆ (2点 - 微妙)",
        rating1: "★☆☆☆☆ (1点 - 不満)"
    },
    ko: {
        siteBrand: "🎬 애니로그 (AniLog)",
        loginTitle: "로그인",
        userId: "아이디",
        userPw: "비밀번호",
        userName: "이름",
        loginBtn: "로그인",
        joinBtn: "회원가입",
        joinTitle: "회원가입",
        joinCompleteBtn: "가입완료",
        boardTitle: "🍿 애니 감상평 & 리뷰 커뮤니티",
        logoutBtn: "로그아웃",
        newPostBtn: "리뷰 작성",
        allPostsTab: "전체 리뷰",
        myPostsTab: "내가 쓴 리뷰",
        noPosts: "해당하는 애니메이션 리뷰가 없습니다.",
        noMyPosts: "내가 작성한 리뷰가 없습니다.",
        welcomeSuffix: "님 환영합니다!",
        writeTitle: "애니메이션 감상평 남기기",
        editTitle: "애니메이션 리뷰 수정하기",
        animeTitleLabel: "애니메이션 제목",
        ratingLabel: "평점 (별점)",
        imageLabel: "애니메이션 포스터/이미지 첨부",
        keepImageNotice: "※새 이미지를 선택하지 않으면 기존 이미지가 그대로 유지됩니다.",
        postTitleLabel: "리뷰 제목",
        postContentLabel: "감상 후기 및 의견",
        saveBtn: "리뷰 등록",
        updateBtn: "수정 완료",
        editBtn: "수정하기",
        deleteBtn: "삭제하기",
        deleteConfirm: "이 리뷰를 정말 삭제하시겠습니까?",
        closeBtn: "닫기",
        backToList: "목록으로",
        searchPlaceholder: "애니 제목 또는 리뷰 키워드로 검색...",
        sortLatest: "최신 등록순",
        sortRatingHigh: "별점 높은 순 (★5→1)",
        sortRatingLow: "별점 낮은 순 (★1→5)",
        commentTitle: "💬 한 줄 댓글",
        commentPlaceholder: "한 줄 감상평을 남겨보세요...",
        commentSubmitBtn: "댓글 등록",
        noComments: "아직 작성된 댓글이 없습니다. 첫 댓글을 남겨보세요!",
        rating5: "★★★★★ (5점 - 최고)",
        rating4: "★★★★☆ (4점 - 추천)",
        rating3: "★★★☆☆ (3점 - 보통)",
        rating2: "★★☆☆☆ (2점 - 아쉬움)",
        rating1: "★☆☆☆☆ (1점 - 비추천)"
    },
    en: {
        siteBrand: "🎬 AniLog",
        loginTitle: "Sign In",
        userId: "Username",
        userPw: "Password",
        userName: "Full Name",
        loginBtn: "Sign In",
        joinBtn: "Create Account",
        joinTitle: "Sign Up",
        joinCompleteBtn: "Register",
        boardTitle: "🍿 Anime Reviews & Ratings Community",
        logoutBtn: "Sign Out",
        newPostBtn: "Write Review",
        allPostsTab: "All Reviews",
        myPostsTab: "My Reviews",
        noPosts: "No anime reviews match your query.",
        noMyPosts: "You haven't written any reviews yet.",
        welcomeSuffix: ", welcome!",
        writeTitle: "Leave an Anime Review",
        editTitle: "Edit Anime Review",
        animeTitleLabel: "Anime Title",
        ratingLabel: "Rating",
        imageLabel: "Anime Poster / Image Upload",
        keepImageNotice: "※If no new image is selected, the current image will be kept.",
        postTitleLabel: "Review Title",
        postContentLabel: "Thoughts & Feedback",
        saveBtn: "Post Review",
        updateBtn: "Update Review",
        editBtn: "Edit",
        deleteBtn: "Delete",
        deleteConfirm: "Are you sure you want to delete this review?",
        closeBtn: "Close",
        backToList: "Back to List",
        searchPlaceholder: "Search by anime or review title...",
        sortLatest: "Latest",
        sortRatingHigh: "Highest Rating (★5→1)",
        sortRatingLow: "Lowest Rating (★1→5)",
        commentTitle: "💬 Comments",
        commentPlaceholder: "Write a short comment...",
        commentSubmitBtn: "Post",
        noComments: "No comments yet. Be the first to comment!",
        rating5: "★★★★★ (5 Stars - Masterpiece)",
        rating4: "★★★★☆ (4 Stars - Great)",
        rating3: "★★★☆☆ (3 Stars - Average)",
        rating2: "★★☆☆☆ (2 Stars - Mediocre)",
        rating1: "★☆☆☆☆ (1 Star - Poor)"
    }
};

let currentLang = localStorage.getItem("siteLang") || "ja";

function applyLanguage(lang) {
    currentLang = lang;
    localStorage.setItem("siteLang", lang);

    document.querySelectorAll("[data-i18n]").forEach(el => {
        const key = el.getAttribute("data-i18n");
        if (i18n[lang] && i18n[lang][key]) {
            el.innerText = i18n[lang][key];
        }
    });

    document.querySelectorAll("[data-i18n-placeholder]").forEach(el => {
        const key = el.getAttribute("data-i18n-placeholder");
        if (i18n[lang] && i18n[lang][key]) {
            el.placeholder = i18n[lang][key];
        }
    });

    document.querySelectorAll("[data-i18n-opt]").forEach(el => {
        const key = el.getAttribute("data-i18n-opt");
        if (i18n[lang] && i18n[lang][key]) {
            el.innerText = i18n[lang][key];
        }
    });

    const btnJa = document.getElementById("btnJa");
    const btnKo = document.getElementById("btnKo");
    const btnEn = document.getElementById("btnEn");
    if (btnJa) btnJa.classList.toggle("active", lang === "ja");
    if (btnKo) btnKo.classList.toggle("active", lang === "ko");
    if (btnEn) btnEn.classList.toggle("active", lang === "en");
}

function changeLanguage(lang) {
    applyLanguage(lang);
}

document.addEventListener("DOMContentLoaded", () => {
    applyLanguage(currentLang);
});