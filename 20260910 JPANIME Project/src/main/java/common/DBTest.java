package common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DBTest {
    public static void main(String[] args) {
        System.out.println("========== [AniLog DB 자동 진단 시작] ==========");

        // 계정 정보 (본인이 사용하는 Oracle 계정/암호로 변경)
        String user = "scott";
        String pass = "tiger";

        // 테스트할 오라클 식별자 후보군
        String[] urls = {
                "jdbc:oracle:thin:@localhost:1521/xepdb1", // 최신 18c/21c XE 플러그형 DB (가장 유력)
                "jdbc:oracle:thin:@localhost:1521:orcl",   // Enterprise/Standard 기본 SID
                "jdbc:oracle:thin:@localhost:1521/orcl",   // 서비스명 orcl
                "jdbc:oracle:thin:@localhost:1521/xe",     // 서비스명 xe
                "jdbc:oracle:thin:@localhost:1521:xe"      // 구버전 11g XE SID
        };

        Connection conn = null;
        String successUrl = null;

        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ 오라클 JDBC 드라이버를 찾을 수 없습니다.");
            return;
        }

        for (String url : urls) {
            System.out.println("👉 접속 시도: " + url);
            try {
                conn = DriverManager.getConnection(url, user, pass);
                if (conn != null) {
                    successUrl = url;
                    System.out.println("🎉 연결 성공! 올바른 URL을 찾았습니다: " + successUrl);
                    break;
                }
            } catch (Exception e) {
                System.out.println("   └ 실패: " + e.getMessage());
            }
        }

        if (conn != null) {
            try {
                String sql = "SELECT USER_ID, USER_NAME FROM USERS WHERE USER_ID = 'test1'";
                try (PreparedStatement pstmt = conn.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("✅ USERS 테이블 조회 성공: ID=" + rs.getString("USER_ID") + ", Name=" + rs.getString("USER_NAME"));
                    } else {
                        System.out.println("⚠️ 연결은 성공했으나 test1 데이터가 없습니다. (DDL 스크립트 실행 필요)");
                    }
                }
                conn.close();
            } catch (Exception e) {
                System.err.println("⚠️ 쿼리 실행 실패 (테이블 미생성 가능성): " + e.getMessage());
            }
        } else {
            System.err.println("❌ 모든 기본 URL 연결에 실패했습니다.");
            System.err.println("👉 계정 아이디(" + user + ") / 비밀번호(" + pass + ") 또는 오라클 리스너 상태를 점검해야 합니다.");
        }
        System.out.println("================================================");
    }
}