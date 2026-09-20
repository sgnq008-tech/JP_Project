package filter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OriginCheckFilterTest {

    @Test
    public void sameHostIsAllowedRegardlessOfPortOrScheme() {
        assertTrue(OriginCheckFilter.isSameHost("http://localhost:8088", "localhost"));
        assertTrue(OriginCheckFilter.isSameHost("https://Example.com/board.html", "example.com"));
    }

    @Test
    public void differentHostIsRejected() {
        assertFalse(OriginCheckFilter.isSameHost("https://evil.example.net", "localhost"));
        // 前方一致を悪用したホスト名も弾く
        assertFalse(OriginCheckFilter.isSameHost("http://localhost.evil.com", "localhost"));
    }

    @Test
    public void unparsableSourceIsRejected() {
        assertFalse(OriginCheckFilter.isSameHost("not a url ::", "localhost"));
    }
}
