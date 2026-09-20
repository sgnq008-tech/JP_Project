package common;

import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * 【クラス名】PasswordUtil
 * 【機能概要】
 *   パスワードの BCrypt ハッシュ化・照合ユーティリティ。
 *   - 新規登録: hash() で BCrypt ハッシュを生成して保存
 *   - ログイン: verify() でハッシュ照合
 *   - 旧データ移行: 平文のまま保存されている既存ユーザーは matchesLegacyPlaintext() で
 *     一度だけ照合し、成功したら hash() で再保存 (LoginServlet 側で実施 = 遅延マイグレーション)
 */
public final class PasswordUtil {

    /** BCrypt のコスト係数 (2^10 回のラウンド)。 */
    private static final int COST = 10;

    /** jBCrypt が生成する形式: $2a$10$ + 53文字 = 全体60文字 */
    private static final Pattern BCRYPT_FORMAT = Pattern.compile("^\\$2a\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private PasswordUtil() {
    }

    /** 平文パスワードから BCrypt ハッシュ (ソルト込み) を生成します。 */
    public static String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(COST));
    }

    /** 保存値が BCrypt ハッシュ形式かどうか。 */
    public static boolean isHashed(String stored) {
        return stored != null && BCRYPT_FORMAT.matcher(stored).matches();
    }

    /** BCrypt ハッシュとの照合。形式不正な保存値は false。 */
    public static boolean verify(String plain, String stored) {
        if (plain == null || !isHashed(stored)) {
            return false;
        }
        try {
            return BCrypt.checkpw(plain, stored);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 旧仕様(平文保存)との照合。タイミング攻撃を避けるため定数時間比較を使用。
     * 保存値がハッシュ形式の場合は常に false を返す (平文扱いさせない)。
     */
    public static boolean matchesLegacyPlaintext(String plain, String stored) {
        if (plain == null || stored == null || isHashed(stored)) {
            return false;
        }
        return MessageDigest.isEqual(
                plain.getBytes(StandardCharsets.UTF_8),
                stored.trim().getBytes(StandardCharsets.UTF_8));
    }
}
