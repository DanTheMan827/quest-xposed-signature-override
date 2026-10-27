package com.example.signinginfooverride;

import java.security.MessageDigest;
import java.util.Base64;

/** Argument layout from the supplied authenticateApplication invocation. */
final class TokenRequestSwap {
    static long version(Object[] args) {
        if (args == null || args.length != 8 || !(args[2] instanceof String)
                || !(args[6] instanceof Integer) || !(args[7] instanceof Boolean))
            throw new IllegalArgumentException("Unsupported token request shape");
        return Integer.toUnsignedLong((Integer) args[6]);
    }

    static boolean apply(Object[] args, String source, String target, String signer) {
        version(args);
        if (!source.equals(args[2])) throw new IllegalArgumentException("Source changed");
        if (source.equals(target)) return false;
        PackageSwaps.pair(source, target);
        if (signer == null || !signer.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Destination signer is unavailable");
        byte[] digest = Base64.getUrlDecoder().decode(signer);
        if (digest.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(digest).equals(signer))
            throw new IllegalArgumentException("Invalid destination certificate hash");
        // Validate first, then change only package and signer. Never chain mappings.
        args[2] = target;
        args[4] = signer;
        return true;
    }

    static String hash(byte[] certificate) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate));
    }
}
