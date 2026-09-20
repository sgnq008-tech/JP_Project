package filter;

import java.io.IOException;
import java.net.URI;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 【フィルター名】OriginCheckFilter
 * 【適用範囲】/api/* への POST リクエスト
 * 【機能概要】
 *   CSRF (クロスサイトリクエストフォージェリ) の簡易対策。
 *   POST の Origin ヘッダー (無ければ Referer) のホスト名が、リクエスト先のホスト名と
 *   一致しない場合は 403 で拒否する。
 *
 *   例) 悪意あるサイトの <form action="https://自サイト/api/board" method="POST"> による
 *       ログイン中ユーザーの意図しない投稿・削除を防ぐ。
 *
 * 【仕様上の割り切り】
 *   - Origin / Referer が両方とも無いリクエスト (curl 等) は許可する。
 *     ブラウザのフォーム送信・fetch は POST で Origin を付けるため、ブラウザ経由の攻撃は防げる。
 *   - リバースプロキシ配下でもホスト名のみ比較するため動作する (ポート・スキームは比較しない)。
 *   - 本格対策は CSRF トークン (同期トークン) の導入。ここでは実装コストの小さい多層防御の一つとして採用。
 */
@WebFilter("/api/*")
public class OriginCheckFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String origin = request.getHeader("Origin");
            String source = (origin != null && !origin.isEmpty() && !"null".equals(origin))
                    ? origin
                    : request.getHeader("Referer");

            if (source != null && !source.isEmpty() && !isSameHost(source, request.getServerName())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-origin request blocked");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    /** source (URL) のホスト名が serverName と一致するか。解析できない場合は不一致扱い。 */
    static boolean isSameHost(String source, String serverName) {
        try {
            String host = new URI(source).getHost();
            return host != null && host.equalsIgnoreCase(serverName);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void destroy() {
    }
}
