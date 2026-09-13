package common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 【クラス名】DBTest
 * 【機能概要】
 *   1. 開発環境におけるOracle Databaseへの接続状態を自動診断
 *   2. 一般的に使用される複数のJDBC接続URL候補(SID/サービス名)を順次自動試行
 *   3. 接続成功時、USERSテーブルに対してテストクエリを実行し、テーブルの存在とテストデータの有無を検証
 */
public class DBTest {
    public static void main(String[] args) {
        System.out.println("========== [AniLog DB 自動診断開始] ==========");

        // [1] 認証アカウント情報 (DDLスクリプトを実行したOracleのユーザー名/パスワード)
        String user = "scott";
        String pass = "tiger";

        // [2] 接続試行対象のOracle JDBC URL候補リスト
        // ※ Oracleのバージョン(11g/18c/21c/Enterprise)やインストール設定による識別子の差異を網羅
        String[] urls = {
                "jdbc:oracle:thin:@localhost:1521/xepdb1", // 18c / 21c Express Edition プラガブルDB (最有力)
                "jdbc:oracle:thin:@localhost:1521:orcl",   // Enterprise / Standard Edition 標準SID
                "jdbc:oracle:thin:@localhost:1521/orcl",   // サービス名指定のorcl
                "jdbc:oracle:thin:@localhost:1521/xe",     // サービス名指定のxe
                "jdbc:oracle:thin:@localhost:1521:xe"      // 旧バージョン 11g Express Edition 標準SID
        };

        Connection conn = null;
        String successUrl = null;

        // [3] Oracle JDBCドライバ(ojdbc)のロード確認
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Oracle JDBCドライバが見つかりません。pom.xmlの依存関係を確認してください。");
            return;
        }

        // [4] URL候補リストを順次接続試行 (生きている接続先を自動検出)
        for (String url : urls) {
            System.out.println("👉 接続試行中: " + url);
            try {
                conn = DriverManager.getConnection(url, user, pass);
                if (conn != null) {
                    successUrl = url;
                    System.out.println("🎉 接続成功! 正しい接続URLを検出しました: " + successUrl);
                    break; // 接続に成功した時点でループを抜ける
                }
            } catch (Exception e) {
                // 接続失敗時(SID不一致・リスナー未応答など)は理由を出力して次の候補へ
                System.out.println("   └ 失敗: " + e.getMessage());
            }
        }

        // [5] 接続成功後のデータ取得テスト (USERSテーブル検証)
        if (conn != null) {
            try {
                // テスト用審査アカウント(test1)の存在確認クエリ
                String sql = "SELECT USER_ID, USER_NAME FROM USERS WHERE USER_ID = 'test1'";
                try (PreparedStatement pstmt = conn.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("✅ USERSテーブル照会成功: ID=" + rs.getString("USER_ID") + ", Name=" + rs.getString("USER_NAME"));
                    } else {
                        System.out.println("⚠️ 接続には成功しましたが、test1データが存在しません。(DDLスクリプトの実行が必要です)");
                    }
                }
                // テスト終了後にコネクションを安全に切断
                conn.close();
            } catch (Exception e) {
                // テーブルが存在しない場合(ORA-00942)などの例外ハンドリング
                System.err.println("⚠️ クエリ実行失敗 (テーブル未作成の可能性): " + e.getMessage());
            }
        } else {
            // 全URLで接続失敗時の案内
            System.err.println("❌ すべての基本URL接続試行に失敗しました。");
            System.err.println("👉 ユーザーID(" + user + ") / パスワード(" + pass + ")、またはOracleリスナー(TNSListener)の稼働状態を確認してください。");
        }
        System.out.println("==============================================");
    }
}