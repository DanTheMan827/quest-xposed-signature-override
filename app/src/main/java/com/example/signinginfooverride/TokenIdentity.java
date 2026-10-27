package com.example.signinginfooverride;

import android.content.Context;

/** Token and package-query certificate selection share the same resolver. */
final class TokenIdentity {
    static CertificateLookup.Result resolve(Context context, String source, String target, long requestVersion) throws Exception {
        PackageSwaps.pair(source, target);
        CertificateResolver resolver = new CertificateResolver(context);
        Long installedSourceVersion = resolver.installedVersion(source);
        long version = installedSourceVersion == null ? requestVersion : installedSourceVersion;
        return CertificateLookup.resolve(resolver, source, version, target);
    }
}
