-- ===================================================
-- 0. 既存オブジェクトの安全な初期化 (クリーンアップ処理)
-- ※ 外部キー制約(CASCADE CONSTRAINTS)を付与し、親子関係の依存順序に関わらず安全に全削除します。
-- ※ EXCEPTION WHEN OTHERS THEN NULL; により、初回実行時に対象テーブルが存在しなくてもエラーで停止しません。
-- ===================================================

-- [0-1] コメントテーブル(子)を強制削除
BEGIN EXECUTE IMMEDIATE 'DROP TABLE REVIEW_COMMENTS CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
-- [0-2] アニメレビュー掲示板テーブル(親)を強制削除
BEGIN EXECUTE IMMEDIATE 'DROP TABLE ANIME_REVIEWS CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
-- [0-3] 後方互換性用のビュー(BOARD)を削除
BEGIN EXECUTE IMMEDIATE 'DROP VIEW BOARD'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
-- [0-4] レガシーBOARDテーブルが存在する場合の安全削除
BEGIN EXECUTE IMMEDIATE 'DROP TABLE BOARD CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
-- [0-5] ユーザーテーブル(最親)を強制削除
BEGIN EXECUTE IMMEDIATE 'DROP TABLE USERS CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
-- [0-6] レビュー番号発番用シーケンスを削除
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_REVIEW_BNO'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
-- [0-7] 旧掲示板番号発番用シーケンスを削除
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_BOARD_BNO'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
-- [0-8] コメント番号発番用シーケンスを削除
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_COMMENT_CNO'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

-- ===================================================
-- 1. ユーザー管理テーブル作成 (USERS)
-- 【機能】会員登録、ログイン認証、セッション管理を行う基盤テーブル
-- ===================================================
CREATE TABLE USERS (
    USER_ID   VARCHAR2(50) PRIMARY KEY,      -- [ユーザーID] 主キー。英数字4〜20文字。重複不可
    USER_PW   VARCHAR2(100) NOT NULL,        -- [パスワード] 必須入力。暗号化または平文パスワード
    PASSWORD  VARCHAR2(100),                 -- [パスワード互換カラム] 既存サーブレットの参照名差異(USER_PW / PASSWORD)を両立するための予備カラム
    USER_NAME VARCHAR2(100) NOT NULL         -- [ヒーローネーム/表示名] サイト上に表示されるニックネーム
);

-- ===================================================
-- 2. アニメレビュー掲示板テーブル作成 (ANIME_REVIEWS)
-- 【機能】アニメの感想投稿、評価(星の数)、サムネイル画像名、閲覧数を管理
-- ===================================================
CREATE TABLE ANIME_REVIEWS (
    BNO          NUMBER PRIMARY KEY,                                  -- [レビュー番号] 主キー。シーケンスにより1から自動採番
    ANIME_TITLE  VARCHAR2(200) NOT NULL,                              -- [アニメ作品名] 必須入力。対象のアニメタイトル
    TITLE        VARCHAR2(200) NOT NULL,                              -- [レビュー見出し] 必須入力。投稿のメインタイトル
    CONTENT      VARCHAR2(4000) NOT NULL,                             -- [レビュー本文] JDBCでの取得エラー(CLOBストリーム問題)を防ぐためVARCHAR2(4000)に最適化
    RATING       NUMBER(1) DEFAULT 5 CHECK (RATING BETWEEN 1 AND 5),  -- [評価点数] 1〜5の整数値のみ許可するCHECK制約。デフォルトは5
    IMAGE_FILE   VARCHAR2(255),                                       -- [画像ファイル名] サーバーの/uploadsディレクトリに保存されたUUIDファイル名
    HIT_COUNT    NUMBER DEFAULT 0,                                    -- [閲覧数] 詳細画面(detail.html)照会時にインクリメント(+1)されるカウンター
    WRITER       VARCHAR2(50) NOT NULL,                               -- [作成者ID] 投稿者のUSER_ID
    REG_DATE     DATE DEFAULT SYSDATE,                                -- [登録日時] 投稿時のシステム日時を自動設定
    CONSTRAINT FK_REVIEW_WRITER FOREIGN KEY (WRITER) 
        REFERENCES USERS(USER_ID) ON DELETE CASCADE                   -- [外部キー制約] ユーザー退会時にそのユーザーのレビューも一括自動削除
);

-- ===================================================
-- 【互換性用ビュー】BOARD
-- 【機能】古いDAOやサーブレットが「SELECT * FROM BOARD」で問い合わせても
--        ANIME_REVIEWSのデータをそのまま返せるようにする仮想テーブル
-- ===================================================
CREATE OR REPLACE VIEW BOARD AS SELECT * FROM ANIME_REVIEWS;

-- ===================================================
-- 3. レビューコメントテーブル作成 (REVIEW_COMMENTS)
-- 【機能】各アニメレビューに対するユーザーの応援コメントや意見を管理
-- ===================================================
CREATE TABLE REVIEW_COMMENTS (
    CNO       NUMBER PRIMARY KEY,                                     -- [コメント番号] 主キー。シーケンスにより1から自動採番
    BNO       NUMBER NOT NULL,                                        -- [対象レビュー番号] どのレビューに対するコメントかを紐付ける外部キー
    WRITER    VARCHAR2(50) NOT NULL,                                  -- [コメント作成者ID] コメントを投稿したユーザーのUSER_ID
    CONTENT   VARCHAR2(1000) NOT NULL,                                -- [コメント本文] 最大1000バイトのテキスト
    REG_DATE  DATE DEFAULT SYSDATE,                                   -- [登録日時] コメント投稿日時(デフォルト: 現在日時)
    CONSTRAINT FK_COMMENT_BNO FOREIGN KEY (BNO) 
        REFERENCES ANIME_REVIEWS(BNO) ON DELETE CASCADE,              -- [連動削除制約] レビューが削除された場合、紐づくコメントも自動削除
    CONSTRAINT FK_COMMENT_WRITER FOREIGN KEY (WRITER) 
        REFERENCES USERS(USER_ID) ON DELETE CASCADE                   -- [連動削除制約] ユーザーが退会した場合、そのユーザーのコメントも自動削除
);

-- ===================================================
-- 4. 自動採番シーケンス作成 (Auto Increment)
-- 【機能】重複しない一意の連番(1, 2, 3...)を高速に発行するOracle専用オブジェクト
-- ===================================================
-- [4-1] ANIME_REVIEWSのBNO発番用 (1から開始、キャッシュなし、ループなし)
CREATE SEQUENCE SEQ_REVIEW_BNO START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
-- [4-2] 旧BOARD参照互換用のBNO発番シーケンス
CREATE SEQUENCE SEQ_BOARD_BNO  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
-- [4-3] REVIEW_COMMENTSのCNO発番用シーケンス
CREATE SEQUENCE SEQ_COMMENT_CNO START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ===================================================
-- 5. 検索パフォーマンス向上のためのインデックス作成 (INDEX)
-- 【機能】データ件数が増加しても、並び替えや条件抽出を高速に行うための索引
-- ===================================================
-- [5-1] 掲示板一覧の最新順ソート(ORDER BY REG_DATE DESC)を高速化
CREATE INDEX IDX_REVIEW_REGDATE ON ANIME_REVIEWS(REG_DATE DESC);
-- [5-2] 特定のユーザーが書いた記事の検索(マイページ等)を高速化
CREATE INDEX IDX_REVIEW_WRITER ON ANIME_REVIEWS(WRITER);
-- [5-3] レビュー詳細画面で該当記事のコメントを瞬時に抽出するためのインデックス
CREATE INDEX IDX_COMMENT_BNO ON REVIEW_COMMENTS(BNO);

-- ===================================================
-- 6. 初期データ投入 (ユーザー・レビュー・コメント)
-- 【機能】ポートフォリオ審査員が即座に動作確認できるよう、テストデータを登録
-- ===================================================

-- ---------------------------------------------------
-- [6-1] テストユーザー登録 (login.htmlのワンクリックログインと連動)
-- ---------------------------------------------------
-- 管理者アカウント
INSERT INTO USERS (USER_ID, USER_PW, PASSWORD, USER_NAME) 
VALUES ('admin', '1234', '1234', 'All Might (管理者)');

-- 一般審査用アカウント (デク)
INSERT INTO USERS (USER_ID, USER_PW, PASSWORD, USER_NAME) 
VALUES ('test1', '1234', '1234', 'Deku (緑谷出久)');

-- 一般ユーザーアカウント (健二)
INSERT INTO USERS (USER_ID, USER_PW, PASSWORD, USER_NAME) 
VALUES ('kenji', '1234', '1234', 'Kenji (健二)');

-- ---------------------------------------------------
-- [6-2] サンプルレビュー登録 (board.htmlのカードグリッド表示用)
-- ---------------------------------------------------
-- レビュー 1: 僕のヒーローアカデミア (BNO=1)
INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, REG_DATE) 
VALUES (
    SEQ_REVIEW_BNO.NEXTVAL,
    '僕のヒーローアカデミア',
    '劇場版＆最新シーズンの神作画と演出が圧倒的！',
    'キャラクター一人ひとりの成長と信念が丁寧に描かれており、クライマックスの戦闘アクションは鳥肌が立つほどの神作画でした。劇伴音楽とのシンクロ率も完璧で文句なしの名作です！ 更に向こうへ、Plus Ultra!!',
    5,
    NULL,
    42,
    'test1',
    SYSDATE - 2
);

-- レビュー 2: 機動戦士ガンダム 閃光のハサウェイ (BNO=2)
INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, REG_DATE) 
VALUES (
    SEQ_REVIEW_BNO.NEXTVAL,
    '機動戦士ガンダム 閃光のハサウェイ',
    '市街地戦のリアリティと夜間戦闘の重厚な演出',
    '従来のメカアクションを超えた音響設計と、緊張感あふれるコックピット視点のカメラワークが素晴らしい。大人の鑑賞に耐えうる重厚なストーリー展開で、次回作への期待が高まる一作です。',
    5,
    NULL,
    35,
    'kenji',
    SYSDATE - 1
);

-- レビュー 3: ちいかわ (BNO=3)
INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, REG_DATE) 
VALUES (
    SEQ_REVIEW_BNO.NEXTVAL,
    'ちいかわ',
    '可愛さの中に潜むシュールさと独特な世界観の魅力',
    '1話あたりの時間は短いですが、テンポの良いコミカルな展開と独特のディストピア感が癖になります。疲れた日常の癒しとして毎朝の楽しみに最適です。',
    4,
    NULL,
    18,
    'admin',
    SYSDATE
);

-- ---------------------------------------------------
-- [6-3] サンプルコメント登録 (detail.html?bno=X のコメント表示用)
-- ---------------------------------------------------
-- レビュー1番に対するコメント (kenji)
INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE)
VALUES (SEQ_COMMENT_CNO.NEXTVAL, 1, 'kenji', 'ヒロアカの劇場版演出、本当に最高でしたよね！完全に同感です。', SYSDATE - 1);

-- レビュー1番に対するコメント (admin)
INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE)
VALUES (SEQ_COMMENT_CNO.NEXTVAL, 1, 'admin', '作画チームの魂を感じるハイクオリティでした。Plus Ultra!!', SYSDATE - 1);

-- レビュー2番に対するコメント (test1)
INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE)
VALUES (SEQ_COMMENT_CNO.NEXTVAL, 2, 'test1', '夜間戦闘のコックピット視点の描写は、何度見ても圧倒されますね。', SYSDATE);

-- ===================================================
-- 7. トランザクション確定処理 (COMMIT)
-- 【機能】ここまでに実行したINSERT・変更処理をデータベースディスクへ永続的に確定保存
-- ===================================================
COMMIT;

-- ===================================================
-- 8. データ登録結果の確認用クエリ (検証用)
-- ===================================================
-- 登録されたユーザー一覧の確認 (3件)
SELECT * FROM USERS;

-- 登録されたレビュー一覧の確認 (3件)
SELECT BNO, ANIME_TITLE, TITLE, WRITER, RATING, HIT_COUNT FROM ANIME_REVIEWS;

-- 登録されたコメント一覧の確認 (3件)
SELECT * FROM REVIEW_COMMENTS;

-- レビューの総件数が正常に「3」と返るか集計確認
SELECT COUNT(*) AS TOTAL_REVIEWS FROM ANIME_REVIEWS;