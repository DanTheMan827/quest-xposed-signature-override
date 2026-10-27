package com.example.signinginfooverride;

import java.util.*;

public final class PackageSwapsTest {
    private static final String SOURCE = "org.example.source", TARGET = "org.example.destination";
    private static final String HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    public static void main(String[] args) throws Exception {
        exactSinglePass(); invalidEntries(); dictionaryLimit(); pairedRequestChanges();
        invalidIdentityPreservesArguments(); requestVersion(); hashFormat();
        System.out.println("7 swap and token argument checks passed");
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void reject(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException expected) {}
    }
    private static Object[] request() {
        return new Object[]{"credential", "application-id", SOURCE, "push-id", "original-signer", "installer", 123, true};
    }

    private static void exactSinglePass() {
        Map<String, String> map = new HashMap<>(); map.put(SOURCE, TARGET); map.put(TARGET, "org.example.third");
        map = PackageSwaps.validate(map);
        check(PackageSwaps.presented(map, SOURCE).equals(TARGET));
        check(PackageSwaps.presented(map, SOURCE + ".extra").equals(SOURCE + ".extra"));
        check(PackageSwaps.presented(Collections.emptyMap(), SOURCE).equals(SOURCE));
    }

    private static void invalidEntries() {
        for (String invalid : new String[]{"", "../org.example", "org.example.*", "org.example;id", " org.example", "org.example+1"})
            reject(() -> PackageSwaps.pair(invalid, TARGET));
        reject(() -> PackageSwaps.pair(SOURCE, SOURCE));
        reject(() -> PackageSwaps.pair(SOURCE, null));
        reject(() -> PackageSwaps.packageName(null));
    }

    private static void dictionaryLimit() {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < 256; i++) map.put("org.example.source" + i, TARGET);
        check(PackageSwaps.validate(map).size() == 256);
        map.put("org.example.extra", TARGET);
        reject(() -> PackageSwaps.validate(map));
    }

    private static void pairedRequestChanges() {
        Object[] args = request(), original = args.clone();
        check(TokenRequestSwap.apply(args, SOURCE, TARGET, HASH));
        check(args[2].equals(TARGET) && args[4].equals(HASH));
        for (int i = 0; i < args.length; i++) if (i != 2 && i != 4) check(args[i] == original[i]);
        Object[] unchanged = request(), copy = unchanged.clone();
        check(!TokenRequestSwap.apply(unchanged, SOURCE, SOURCE, null));
        check(Arrays.equals(unchanged, copy));
    }

    private static void invalidIdentityPreservesArguments() {
        for (String signer : new String[]{null, "", "not-a-hash", HASH.substring(0, 42) + "B"}) {
            Object[] args = request(), before = args.clone();
            reject(() -> TokenRequestSwap.apply(args, SOURCE, TARGET, signer));
            check(Arrays.equals(args, before));
        }
        Object[] args = request(), before = args.clone();
        reject(() -> TokenRequestSwap.apply(args, SOURCE, "../invalid", HASH));
        check(Arrays.equals(args, before));
    }

    private static void requestVersion() {
        Object[] args = request(); args[6] = -1;
        check(TokenRequestSwap.version(args) == 4294967295L);
        reject(() -> TokenRequestSwap.version(new Object[7]));
        args[7] = "incorrect"; reject(() -> TokenRequestSwap.version(args));
    }

    private static void hashFormat() throws Exception {
        check(TokenRequestSwap.hash(new byte[0]).equals("47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU"));
    }
}
