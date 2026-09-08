package common;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBConn {

    // データベース接続オブジェクトの生成
    public static Connection getConnection() {
        Connection conn = null;
        try {
            // Oracle JDBC ドライバのロード
            Class.forName("oracle.jdbc.OracleDriver");
            
            // 接続文字列および認証情報
            String url = "jdbc:oracle:thin:@localhost:1521/orcl";
            String id = "scott";
            String pw = "tiger";

            conn = DriverManager.getConnection(url, id, pw);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return conn;
    }

    // 各種リソース（Statement, ResultSet, Connection）の安全なクローズ処理
    public static void close(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}