package com.example.signinginfooverride;

/** One certificate-resolution policy for package queries and token requests. */
final class CertificateLookup {
    interface Backend {
        Long installedVersion(String packageName) throws Exception;
        byte[] savedPem(String packageName, long version) throws Exception;
        byte[] installedPem(String packageName) throws Exception;
    }

    static final class Result {
        final byte[] pem;
        final String packageName, origin;
        final long version;
        Result(byte[] pem, String packageName, long version, String origin) throws Exception {
            OverrideFiles.certificates(pem);
            this.pem = pem; this.packageName = packageName; this.version = version; this.origin = origin;
        }
    }

    static Result resolve(Backend backend, String source, long sourceVersion, String target) throws Exception {
        PackageSwaps.packageName(source); PackageSwaps.packageName(target);
        byte[] pem = backend.savedPem(source, sourceVersion);
        if (pem != null) return new Result(pem, source, sourceVersion, "source override");
        // An unconfigured package must retain its real signatures, not receive a synthetic override.
        if (source.equals(target)) return null;
        Long installedVersion = backend.installedVersion(target);
        long targetVersion = installedVersion == null ? sourceVersion : installedVersion;
        pem = backend.savedPem(target, targetVersion);
        if (pem != null) return new Result(pem, target, targetVersion, "destination override");
        if (installedVersion == null) return null;
        pem = backend.installedPem(target);
        return pem == null ? null : new Result(pem, target, targetVersion, "destination installed signer");
    }

    static String tokenSigner(Result identity) throws Exception {
        if (identity == null) throw new IllegalArgumentException("No usable signing certificate for this swap");
        java.util.List<java.security.cert.X509Certificate> certificates = OverrideFiles.certificates(identity.pem);
        if (certificates.size() != 1) throw new IllegalArgumentException("Token requests require exactly one signer");
        return TokenRequestSwap.hash(certificates.get(0).getEncoded());
    }
}
