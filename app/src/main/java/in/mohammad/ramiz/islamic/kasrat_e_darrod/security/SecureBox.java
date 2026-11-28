package in.mohammad.ramiz.islamic.kasrat_e_darrod.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM with a key that lives in the Android Keystore and never leaves
 * it (it can't be copied off the phone, not even from a backup or a rooted
 * file dump). Used for the sign-in tokens and the on-disk API cache.
 *
 * Format: [1 byte version][12 byte IV][ciphertext + 16 byte tag].
 * Strings: "k1:" + Base64 of that.
 */
public final class SecureBox {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "kasrat_box_v1";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final byte VERSION = 1;
    private static final int IV_LEN = 12, TAG_BITS = 128;
    public static final String PREFIX = "k1:";

    @Nullable private static volatile SecretKey key;

    private SecureBox() { }

    private static SecretKey key() throws Exception {
        SecretKey k = key;
        if (k != null) return k;
        synchronized (SecureBox.class) {
            if (key != null) return key;
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) {
                key = ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
            } else {
                KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
                gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build());
                key = gen.generateKey();
            }
            return key;
        }
    }

    /** Encrypted bytes, or null if the Keystore is unavailable. */
    @Nullable
    public static byte[] encrypt(@NonNull byte[] plain) {
        try {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain);
            return ByteBuffer.allocate(1 + iv.length + ct.length).put(VERSION).put(iv).put(ct).array();
        } catch (Exception e) {
            return null;
        }
    }

    /** Plain bytes, or null if the data is damaged or the key is gone. */
    @Nullable
    public static byte[] decrypt(@Nullable byte[] box) {
        if (box == null || box.length < 1 + IV_LEN + 16 || box[0] != VERSION) return null;
        try {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, box, 1, IV_LEN));
            return c.doFinal(box, 1 + IV_LEN, box.length - 1 - IV_LEN);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String encryptString(@Nullable String plain) {
        if (plain == null) return null;
        byte[] box = encrypt(plain.getBytes(StandardCharsets.UTF_8));
        return box == null ? null : PREFIX + Base64.encodeToString(box, Base64.NO_WRAP);
    }

    /** Decrypts a value made by {@link #encryptString}; null if it can't be read. */
    @Nullable
    public static String decryptString(@Nullable String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return null;
        try {
            byte[] plain = decrypt(Base64.decode(stored.substring(PREFIX.length()), Base64.NO_WRAP));
            return plain == null ? null : new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isSealed(@Nullable String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
