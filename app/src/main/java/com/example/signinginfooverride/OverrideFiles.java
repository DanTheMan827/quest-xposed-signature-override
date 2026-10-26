package com.example.signinginfooverride;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared, Android-independent format used by the hook, manager and tests. */
final class OverrideFiles {
    static final int MAX_BYTES = 65536;
    private static final Pattern NAME = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+)\\+(0|[1-9][0-9]*)\\.pem");

    static final class Key {
        final String packageName;
        final long version;
        Key(String packageName, long version) { this.packageName = packageName; this.version = version; }
        String filename() { return packageName + "+" + version + ".pem"; }
    }

    static Key parse(String filename) {
        Matcher match = NAME.matcher(filename);
        if (!match.matches()) throw new IllegalArgumentException("Expected package.name+versionCode.pem");
        return new Key(match.group(1), Long.parseLong(match.group(2)));
    }

    static String filename(String packageName, long version) {
        return parse(packageName + "+" + version + ".pem").filename();
    }

    static File resolve(File directory, String filename) throws IOException {
        parse(filename);
        File base = directory.getCanonicalFile();
        File file = new File(base, filename).getCanonicalFile();
        if (!base.equals(file.getParentFile())) throw new IOException("Override escapes directory");
        return file;
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > MAX_BYTES) throw new IOException("Certificate exceeds 64 KiB");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    static byte[] read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) { return read(in); }
    }

    static List<X509Certificate> certificates(byte[] bytes) throws Exception {
        if (bytes.length > MAX_BYTES) throw new IOException("Certificate file exceeds 64 KiB");
        String text = new String(bytes, StandardCharsets.US_ASCII).trim();
        String block = "-----BEGIN CERTIFICATE-----\\s+[A-Za-z0-9+/=\\r\\n]+\\s+-----END CERTIFICATE-----";
        if (!text.matches("(?s)(?:" + block + "\\s*)+")) {
            throw new IllegalArgumentException("Supply only public PEM certificates");
        }
        Collection<? extends java.security.cert.Certificate> parsed = CertificateFactory.getInstance("X.509")
                .generateCertificates(new ByteArrayInputStream(bytes));
        if (parsed.isEmpty() || parsed.size() > 16) throw new IllegalArgumentException("Expected 1–16 signer certificates");
        List<X509Certificate> result = new ArrayList<>();
        for (java.security.cert.Certificate certificate : parsed) {
            if (result.contains(certificate)) throw new IllegalArgumentException("Duplicate signer certificate");
            result.add((X509Certificate) certificate);
        }
        return result;
    }

    static X509Certificate certificate(byte[] bytes) throws Exception {
        List<X509Certificate> result = certificates(bytes);
        if (result.size() != 1) throw new IllegalArgumentException("Expected exactly one certificate");
        return result.get(0);
    }

    static byte[] pem(List<X509Certificate> certificates) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (X509Certificate certificate : certificates) out.write(pem(certificate));
        byte[] bytes = out.toByteArray();
        certificates(bytes);
        return bytes;
    }

    static byte[] pem(X509Certificate certificate) throws Exception {
        return ("-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(certificate.getEncoded()) + "\n-----END CERTIFICATE-----\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    static String fingerprint(X509Certificate certificate) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())) {
            if (result.length() > 0) result.append(':');
            result.append(String.format(java.util.Locale.ROOT, "%02X", b & 255));
        }
        return result.toString();
    }
}
