-- ===================================================
-- AniLog v2 マイグレーション (既存DBを v1 → v2 へ更新するスクリプト)
-- ※ 新規に環境を作る場合は「JP DB PROJECT.sql」だけ実行すればよく、本スクリプトは不要です。
-- ※ 実行前にバックアップ(expdp 等)を取ってください。何度実行しても壊れないように書いています。
-- ===================================================

-- [1] 旧 PASSWORD 互換カラムの値を USER_PW へ寄せる (USER_PW が空の行のみ)
--     PASSWORD カラムが既に存在しない環境では ORA-00904 になるため、例外は無視します。
BEGIN
    EXECUTE IMMEDIATE 'UPDATE USERS SET USER_PW = PASSWORD WHERE USER_PW IS NULL AND PASSWORD IS NOT NULL';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- [2] 平文パスワードが残る PASSWORD カラムを削除 (アプリは USER_PW のみ使用)
--     ※ USER_PW 側の平文は、各ユーザーの次回ログイン成功時にアプリが BCrypt へ自動置換します。
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE USERS DROP COLUMN PASSWORD';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- [3] 文字数セマンティクスへ変更
--     v1 は「バイト」指定のため、日本語(UTF-8で3バイト)のタイトルが ORA-12899 で登録できない場合がありました。
ALTER TABLE ANIME_REVIEWS MODIFY (ANIME_TITLE VARCHAR2(200 CHAR));
ALTER TABLE ANIME_REVIEWS MODIFY (TITLE VARCHAR2(200 CHAR));
ALTER TABLE ANIME_REVIEWS MODIFY (CONTENT VARCHAR2(4000 CHAR));
ALTER TABLE REVIEW_COMMENTS MODIFY (CONTENT VARCHAR2(1000 CHAR));
ALTER TABLE USERS MODIFY (USER_NAME VARCHAR2(100 CHAR));

COMMIT;

-- [確認] USERS のカラム構成 (PASSWORD が消えていること)
SELECT COLUMN_NAME, DATA_TYPE, CHAR_LENGTH FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'USERS' ORDER BY COLUMN_ID;
