package com.example.signinginfooverride;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

/** Bounded local reads and exact package/version matching; no token processing. */
final class OverrideConfig {
    private static final int MAX_BYTES = 65536;
    private final File configFile;

    OverrideConfig(String path) { configFile = new File(path); }

    Document read() throws Exception {
        JSONObject root = new JSONObject(new String(readBounded(configFile), StandardCharsets.UTF_8));
        JSONArray entries = root.getJSONArray("overrides");
        if (entries.length() > 256) throw new IllegalArgumentException("At most 256 overrides supported");
        return new Document(configFile.getCanonicalFile().getParentFile(), entries);
    }

    static final class Document {
        private final File base;
        private final JSONArray entries;

        Document(File base, JSONArray entries) { this.base = base; this.entries = entries; }

        boolean hasPackage(String packageName) throws Exception {
            for (int i = 0; i < entries.length(); i++) {
                if (packageName.equals(entries.getJSONObject(i).getString("packageName"))) return true;
            }
            return false;
        }

        String hashFor(String packageName, long version) throws Exception {
            JSONObject match = null;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (!packageName.equals(entry.getString("packageName"))) continue;
                Object number = entry.get("versionCode");
                if (!(number instanceof Integer) && !(number instanceof Long)) {
                    throw new IllegalArgumentException("versionCode must be a JSON integer");
                }
                long configuredVersion = ((Number) number).longValue();
                if (configuredVersion < 0) throw new IllegalArgumentException("versionCode must not be negative");
                if (configuredVersion != version) continue;
                if (match != null) throw new IllegalArgumentException("Duplicate package/version override");
                match = entry;
            }
            if (match == null) return null;
            boolean cert = match.has("certificateFile");
            boolean digest = match.has("sha256");
            if (cert == digest) throw new IllegalArgumentException("Specify exactly one of certificateFile or sha256");
            if (digest) return normalizeHash(match.getString("sha256"));

            String relative = match.getString("certificateFile");
            if (relative.isEmpty() || new File(relative).isAbsolute()) {
                throw new IllegalArgumentException("certificateFile must be relative to the config directory");
            }
            File certificate = new File(base, relative).getCanonicalFile();
            if (!certificate.getPath().startsWith(base.getPath() + File.separator)) {
                throw new IllegalArgumentException("certificateFile must stay inside the config directory");
            }
            Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(readBounded(certificate)));
            if (certificates.size() != 1) throw new IllegalArgumentException("Supply exactly one X.509 certificate");
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificates.iterator().next().getEncoded());
            return encode(hash);
        }
    }

    private static String normalizeHash(String value) {
        byte[] hash;
        if (value.matches("[0-9A-Fa-f]{64}")) {
            hash = new byte[32];
            for (int i = 0; i < hash.length; i++) {
                hash[i] = (byte) Integer.parseInt(value.substring(2 * i, 2 * i + 2), 16);
            }
        } else if (value.matches("[A-Za-z0-9_-]{43}")) {
            hash = Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (hash.length != 32 || !encode(hash).equals(value)) {
                throw new IllegalArgumentException("Noncanonical Base64URL SHA-256 digest");
            }
        } else {
            throw new IllegalArgumentException("sha256 must be 64 hex or 43 unpadded Base64URL characters");
        }
        return encode(hash);
    }

    private static String encode(byte[] hash) {
        return Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] readBounded(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (data.size() + count > MAX_BYTES) throw new IOException("File exceeds 64 KiB: " + file);
                data.write(buffer, 0, count);
            }
            return data.toByteArray();
        }
    }
}
