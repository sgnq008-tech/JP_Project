package common;

import com.google.gson.Gson;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

/**
 * 【クラス名】Json
 * 【機能概要】
 *   JSONレスポンス出力の共通ヘルパー (Gson)。
 *   従来は各サーブレットが escapeJson() と文字列連結で JSON を組み立てていたが、
 *   エスケープ漏れ(制御文字・Unicode 等)による構文破壊のリスクがあるため Gson に統一した。
 */
public final class Json {

    private static final Gson GSON = new Gson();

    private Json() {
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    /** Content-Type を UTF-8 JSON に設定して value を出力します。 */
    public static void write(HttpServletResponse response, Object value) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(GSON.toJson(value));
    }
}
