package com.example.signinginfooverride;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Exact, one-pass package-name dictionary; no prefix matching or recursive swaps. */
final class PackageSwaps {
    static final int MAX_ENTRIES = 256;
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*");

    static String packageName(String value) {
        if (value == null || value.length() > 255 || !PACKAGE.matcher(value).matches())
            throw new IllegalArgumentException("Enter an Android package ID such as org.example.application");
        return value;
    }

    static void pair(String source, String target) {
        packageName(source); packageName(target);
        if (source.equals(target)) throw new IllegalArgumentException("Source and presented bundle IDs must differ");
    }

    static Map<String, String> validate(Map<String, String> swaps) {
        if (swaps.size() > MAX_ENTRIES) throw new IllegalArgumentException("At most 256 swaps are supported");
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<String, String> entry : swaps.entrySet()) {
            pair(entry.getKey(), entry.getValue());
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    static String presented(Map<String, String> swaps, String source) {
        packageName(source);
        String target = swaps.get(source);
        if (target == null) return source;
        pair(source, target);
        return target;
    }
}
