# AniLog v2 変更点 (v1 → v2)

## 適用手順
1. **既存DBの場合**: `JPANIME Project_DB/migration_v2.sql` を実行 (新規環境は `JP DB PROJECT.sql` のみ)
2. `JPANIME Project/` を既存プロジェクトに上書きコピー (`pom.xml` に依存追加あり → `mvn clean package`)
3. (任意) 環境変数: `JPANIME_DB_URL` / `JPANIME_DB_USER` / `JPANIME_DB_PASS` / `JPANIME_UPLOAD_DIR`
   未指定なら開発用デフォルト (scott/tiger, 自動URL探索) で動作

## セキュリティ
- パスワードを BCrypt 化 (`common/PasswordUtil`)。既存の平文ユーザーは初回ログイン成功時に自動でハッシュへ移行
- デモアカウントのバイパスログイン廃止 / ログイン失敗ログからパスワード出力を削除
- 画像削除のパストラバーサル修正 (クライアント送信の `existingImage` を信用せず DB の値を使用)
- 画像アップロード検証 (拡張子ホワイトリスト + Content-Type + マジックナンバー)
- POST の Origin/Referer 検査フィルター (`filter/OriginCheckFilter`) / セッション Cookie HttpOnly
- コメントAPI: 例外メッセージをレスポンスに含めない、存在しないレビューへの投稿を拒否

## 構造
- HikariCP コネクションプール + DB接続情報の環境変数化 (`common/DBConn`)
- Gson による JSON 生成 (`common/Json`)
- 入力長・評価値(1〜5)のサーバー側検証

## 機能
- レビュー一覧: 検索 / 並び替え / ページネーション / 「自分のレビューのみ」 (Oracle 11g 互換の ROWNUM)
  - `api/board` の一覧レスポンスは `{items, total, page, size, totalPages}` に変更
- コメント削除 (本人のみ) / 閲覧数の重複加算防止 / 編集画面で閲覧数を加算しない
- 感情分析: 英語の単語境界判定と否定表現対応、辞書の誤字修正、`lang` パラメータ対応
- 多言語文言の補完、モバイル対応CSS (board / detail)

## テスト
- `src/test/java`: ReviewIntelligenceTest / PasswordUtilTest / OriginCheckFilterTest (JUnit 4, `mvn test`)

## 既知の限界 / 次のステップ
- 日本語・韓国語の感情分析は部分一致のみ (否定形は未対応) → Kuromoji / LLM API へ
- CSRF は簡易対策 (本格対応はトークン方式)
- 作品テーブルの正規化、いいね/ブックマーク、Spring Boot 版、Docker 化、日本語README は未実装
