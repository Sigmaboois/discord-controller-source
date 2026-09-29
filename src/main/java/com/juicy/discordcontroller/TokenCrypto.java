package com.juicy.discordcontroller;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class TokenCrypto {

    private static final int IV_BYTES = 12;
    private static final int ITERATIONS = 200_000;
    private static final byte[] TAG = "discord-controller-token-v1".getBytes(StandardCharsets.UTF_8);

    private TokenCrypto() {
    }

    public static byte[] random(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    public static byte[] deriveKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    public static String verifier(byte[] key) throws Exception {
        return Base64.getEncoder().encodeToString(hmac(key, TAG));
    }

    public static boolean matches(byte[] key, String verifierB64) {
        try {
            byte[] expect = Base64.getDecoder().decode(verifierB64);
            return MessageDigest.isEqual(hmac(key, TAG), expect);
        } catch (Exception e) {
            return false;
        }
    }

    public static String[] encrypt(byte[] key, String plaintext) throws Exception {
        byte[] iv = random(IV_BYTES);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return new String[]{
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(ct)
        };
    }

    public static String decrypt(byte[] key, String ivB64, String ctB64) throws Exception {
        byte[] iv = Base64.getDecoder().decode(ivB64);
        byte[] ct = Base64.getDecoder().decode(ctB64);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
