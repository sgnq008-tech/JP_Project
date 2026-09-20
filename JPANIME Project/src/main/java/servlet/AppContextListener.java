package servlet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import common.DBConn;

/**
 * 【リスナー名】AppContextListener
 * 【機能概要】
 *   アプリケーション停止(再デプロイ含む)時に DB コネクションプールを閉じる。
 *   閉じないと再デプロイのたびにスレッド・接続が残りリークする。
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // プールは初回アクセス時に遅延初期化するため、ここでは何もしない
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        DBConn.shutdown();
    }
}
