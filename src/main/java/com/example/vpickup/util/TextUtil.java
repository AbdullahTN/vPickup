package com.example.vpickup.util;

import org.bukkit.Material;

/**
 * Lightweight text utilities for the vPickup action bar display.
 *
 * Converts ASCII text to Unicode Small Capitals and wraps numbers in
 * Mathematical Monospace digits so the action bar looks polished without
 * any external font / resource pack.
 */
public final class TextUtil {

    private TextUtil() {}

    // ── Small-Caps table (A-Z → ᴀ-ᴢ) ─────────────────────────────────────────

    private static final char[] SMALL_CAPS = {
        'ᴀ','ʙ','ᴄ','ᴅ','ᴇ','ꜰ','ɢ','ʜ','ɪ','ᴊ',
        'ᴋ','ʟ','ᴍ','ɴ','ᴏ','ᴘ','ꞯ','ʀ','s','ᴛ',
        'ᴜ','ᴠ','ᴡ','x','ʏ','ᴢ'
    };

    // Mathematical Monospace digits 𝟶-𝟿 (U+1D7F6 … U+1D7FF)
    private static final int MONO_ZERO = 0x1D7F6;

    /**
     * Converts a string to small-caps Unicode.
     * Non-alphabetic characters (spaces, punctuation) are left unchanged.
     */
    public static String toSmallCaps(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                sb.append(SMALL_CAPS[c - 'a']);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(SMALL_CAPS[c - 'A']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts a non-negative integer to Mathematical Monospace digit string.
     * e.g. 64 → "𝟼𝟺"
     */
    public static String toMonoDigits(int n) {
        String digits = Integer.toString(n);
        StringBuilder sb = new StringBuilder(digits.length() * 2); // code points may be 2 chars
        for (char d : digits.toCharArray()) {
            int codePoint = MONO_ZERO + (d - '0');
            sb.appendCodePoint(codePoint);
        }
        return sb.toString();
    }

    /**
     * Converts a Material name (e.g. LAPIS_LAZULI) to a pretty small-caps label.
     * e.g. LAPIS_LAZULI → ʟᴀᴘɪs ʟᴀᴢᴜʟɪ
     */
    public static String materialToLabel(Material mat) {
        String raw = mat.name().replace('_', ' ').toLowerCase();
        return toSmallCaps(raw);
    }
}
