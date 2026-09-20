package common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 【クラス名】DBConn
 * 【機能概要】
 *   1. Oracle Database 接続の一元管理 (HikariCP コネクションプール)
 *      - 従来は「リクエスト毎に DriverManager で新規接続」していたため、アクセス増加で接続数が枯渇する構造だった
 *      - プール化により接続を再利用し、最大接続数も制御できる
 *   2. 接続情報の外部化 (環境変数)
 *      - JPANIME_DB_URL  : JDBC URL (未指定なら下記の候補を順に試して最初に繋がったものを採用)
 *      - JPANIME_DB_USER : DBユーザー (未指定時は開発用デフォルト scott)
 *      - JPANIME_DB_PASS : DBパスワード (未指定時は開発用デフォルト tiger)
 *      ※ 本番・公開環境では必ず環境変数で上書きすること (デフォルト値はローカル開発専用)
 *   3. AutoCloseable リソースの安全な一括クローズ
 *
 * 【互換性】getConnection() / close(...) のシグネチャは従来のまま。
 *   プール経由の Connection#close() は「物理切断」ではなく「プールへ返却」になる。
 */
public class DBConn {

    // 開発用デフォルト (環境変数が無い場合のみ使用)
    private static final String DEFAULT_USER = "scott";
    private static final String DEFAULT_PASS = "tiger";

    // 接続試行対象の JDBC URL 候補 (JPANIME_DB_URL 未指定時のみ。優先順に自動探索)
    private static final String[] URL_CANDIDATES = {
            "jdbc:oracle:thin:@localhost:1521:xe",        // 11g Express Edition
            "jdbc:oracle:thin:@localhost:1521:orcl",      // Enterprise / Standard Edition 標準SID
            "jdbc:oracle:thin:@localhost:1521/xepdb1",    // 18c / 21c Express Edition プラガブルDB
            "jdbc:oracle:thin:@localhost:1521:XE"         // 大文字指定環境向け予備候補
    };

    private static volatile HikariDataSource dataSource;

    static {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConn] Oracle JDBCドライバのロードに失敗しました: " + e.getMessage());
        }
    }

    private DBConn() {
    }

    /**
     * プールから Connection を取得します。
     *
     * @return 有効な Connection (使用後は close() でプールへ返却)、DB未接続時は null
     */
    public static Connection getConnection() {
        try {
            HikariDataSource ds = pool();
            return (ds == null) ? null : ds.getConnection();
        } catch (SQLException e) {
            System.err.println("[DBConn] コネクション取得に失敗しました: " + e.getMessage());
            return null;
        }
    }

    /** プールの遅延初期化 (ダブルチェックロッキング)。DB未起動で失敗した場合は次回呼び出し時に再試行。 */
    private static HikariDataSource pool() {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            synchronized (DBConn.class) {
                ds = dataSource;
                if (ds == null) {
                    ds = createPool();
                    dataSource = ds;
                }
            }
        }
        return ds;
    }

    private static HikariDataSource createPool() {
        String user = env("JPANIME_DB_USER", DEFAULT_USER);
        String pass = env("JPANIME_DB_PASS", DEFAULT_PASS);
        String explicitUrl = env("JPANIME_DB_URL", null);

        if (System.getenv("JPANIME_DB_USER") == null || System.getenv("JPANIME_DB_PASS") == null) {
            System.err.println("[DBConn] 警告: DB認証情報が環境変数で指定されていないため開発用デフォルトを使用します。");
        }

        List<String> urls = (explicitUrl != null)
                ? Collections.singletonList(explicitUrl)
                : Arrays.asList(URL_CANDIDATES);

        for (String url : urls) {
            // まず1本だけ素の接続を試し、繋がる URL を特定してからプールを作る
            try (Connection probe = DriverManager.getConnection(url, user, pass)) {
                if (probe == null) {
                    continue;
                }
            } catch (Exception e) {
                continue; // SID不一致など → 次の候補へ
            }

            HikariConfig cfg = new HikariConfig();
            cfg.setPoolName("JPAnimePool");
            cfg.setDriverClassName("oracle.jdbc.OracleDriver");
            cfg.setJdbcUrl(url);
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setMaximumPoolSize(10);
            cfg.setMinimumIdle(2);
            cfg.setConnectionTimeout(5000);   // 5秒で取得できなければ失敗させる
            cfg.setAutoCommit(true);
            System.out.println("[DBConn] コネクションプールを初期化しました: " + url);
            return new HikariDataSource(cfg);
        }

        System.err.println("[DBConn] すべてのOracle接続試行に失敗しました。JPANIME_DB_URL/USER/PASS とリスナーの状態を確認してください。");
        return null;
    }

    /** アプリケーション停止時にプールを閉じる (AppContextListener から呼ばれる)。 */
    public static void shutdown() {
        synchronized (DBConn.class) {
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
        }
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v == null || v.trim().isEmpty()) ? defaultValue : v.trim();
    }

    /**
     * ResultSet / Statement / Connection など AutoCloseable を可変長引数で受け取り、安全にクローズします。
     * (Connection の場合はプールへ返却されます)
     */
    public static void close(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception ignored) {
                    // クローズ中の例外は無視し、後続リソースのクローズを継続
                }
            }
        }
    }
}
