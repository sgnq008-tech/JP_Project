package common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordUtilTest {

    @Test
    public void hashedPasswordVerifiesAndIsNotPlaintext() {
        String hash = PasswordUtil.hash("secret-pw");
        assertNotEquals("secret-pw", hash);
        assertEquals(60, hash.length());
        assertTrue(PasswordUtil.isHashed(hash));
        assertTrue(PasswordUtil.verify("secret-pw", hash));
        assertFalse(PasswordUtil.verify("wrong-pw", hash));
    }

    @Test
    public void sameInputProducesDifferentHashesBecauseOfSalt() {
        assertNotEquals(PasswordUtil.hash("1234"), PasswordUtil.hash("1234"));
    }

    @Test
    public void legacyPlaintextIsRecognizedOnlyForNonHashedValues() {
        assertTrue(PasswordUtil.matchesLegacyPlaintext("1234", "1234"));
        assertFalse(PasswordUtil.matchesLegacyPlaintext("1234", "9999"));
        // ハッシュ形式の保存値は「平文」として照合させない (ハッシュ文字列そのものでログインされるのを防ぐ)
        String hash = PasswordUtil.hash("1234");
        assertFalse(PasswordUtil.matchesLegacyPlaintext(hash, hash));
    }

    @Test
    public void verifyRejectsNonHashedStoredValue() {
        assertFalse(PasswordUtil.verify("1234", "1234"));
        assertFalse(PasswordUtil.verify("1234", null));
    }
}
