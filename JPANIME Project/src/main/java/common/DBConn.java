package common;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * 【クラス名】DBConn
 * 【機能概要】
 *   1. アプリケーション全体で共有されるOracle Database接続ユーティリティ
 *   2. 実行環境(11g XE / 18c・21c XE / Enterprise)ごとのSID・サービス名差異を自動吸収するフォールバック接続機構
 *   3. ResultSet、Statement、Connectionなどのリソースリークを防ぐ安全な一括クローズメソッドの提供
 */
public class DBConn {

    // [1] Oracle認証アカウント情報 (DDLスクリプトを実行したユーザー名およびパスワード)
    private static final String DB_USER = "scott";
    private static final String DB_PASS = "tiger";

    // [2] 接続試行対象のJDBC URL候補リスト (優先順に自動探索)
    // ※ 開発端末ごとのOracleインスタンス設定の差異に対応
    private static final String[] URL_CANDIDATES = {
            "jdbc:oracle:thin:@localhost:1521:xe",        // 11g Express Edition (最も一般的)
            "jdbc:oracle:thin:@localhost:1521:orcl",      // Enterprise / Standard Edition 標準SID
            "jdbc:oracle:thin:@localhost:1521/xepdb1",    // 18c / 21c Express Edition プラガブルDB
            "jdbc:oracle:thin:@localhost:1521:XE"         // 大文字指定環境向け予備候補
    };

    // [3] クラスロード時にOracle JDBCドライバを初期化
    static {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConn] ❌ Oracle JDBCドライバのロードに失敗しました: " + e.getMessage());
        }
    }

    /**
     * Oracle DB接続(Connection)を取得します。
     * 候補リスト内の各URLに対して順次接続を試行し、最初に接続確立できたコネクションを返却します。
     *
     * @return 有効なConnectionインスタンス、全接続失敗時はnull
     */
    public static Connection getConnection() {
        Connection conn = null;

        for (String url : URL_CANDIDATES) {
            try {
                conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
                // 接続が確立され、かつクローズされていないことを確認
                if (conn != null && !conn.isClosed()) {
                    return conn;
                }
            } catch (Exception ignored) {
                // 該当URLで接続できない場合(SID不一致等)は例外を無視し、次の候補URLへフォールバック
            }
        }

        // すべての接続候補で失敗した場合のエラーログ
        System.err.println("[DBConn] ❌ すべてのOracle接続試行に失敗しました (xe / orcl / xepdb1)。ユーザー名(" + DB_USER + ")またはOracleリスナーの状態を確認してください。");
        return null;
    }

    /**
     * ResultSet、Statement、PreparedStatement、Connectionなど、
     * AutoCloseableインターフェースを実装したリソースを可変長引数で受け取り、安全にクローズします。
     *
     * @param resources クローズ対象のDBリソース群 (nullセーフ)
     */
    public static void close(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception ignored) {
                    // クローズ処理中の例外は安全に無視し、後続リソースのクローズを継続
                }
            }
        }
    }
}