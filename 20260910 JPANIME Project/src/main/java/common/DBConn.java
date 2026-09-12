package common;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBConn {

    public static Connection getConnection() {
        Connection conn = null;
        try {
            Class.forName("oracle.jdbc.OracleDriver");

            // ==========================================
            // [1] 가장 보편적인 Oracle XE (11g) 설정
            // ==========================================
            String url = "jdbc:oracle:thin:@localhost:1521:orcl";

            // 만약 위 URL로 ORA-12505가 다시 발생하고,
            // Oracle 18c/21c XE 버전을 쓰신다면 아래 줄의 주석을 풀고 위 줄을 주석(//) 처리하세요:
            // String url = "jdbc:oracle:thin:@localhost:1521/xepdb1";

            // ==========================================
            // [2] 계정 정보
            // ==========================================
            // 아까 DDL 스크립트를 실행했던 오라클 사용자 계정 및 비밀번호를 적어주세요.
            String id = "scott";
            String pw = "tiger";

            conn = DriverManager.getConnection(url, id, pw);
        } catch (Exception e) {
            System.err.println("[AniLog DBConn] DB 연결 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return conn;
    }

    public static void close(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception ignored) {}
            }
        }
    }
}