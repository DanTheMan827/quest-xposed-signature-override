package com.example.signinginfooverride;

import java.io.InputStream;
import java.util.*;

public final class CertificateLookupTest {
    private static final String SOURCE = "org.example.copy", TARGET = "org.example.original";
    private static final long SOURCE_VERSION = 3237, TARGET_VERSION = 4294967303L;
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static byte[] fixture(String name) throws Exception {
        try (InputStream in = CertificateLookupTest.class.getResourceAsStream("/" + name)) { return OverrideFiles.read(in); }
    }
    private static final class Backend implements CertificateLookup.Backend {
        final Map<String, byte[]> files = new HashMap<>(), installed = new HashMap<>();
        final Map<String, Long> versions = new HashMap<>();
        boolean forbidDestination;
        int versionReads, installedReads;
        void save(String id, long version, byte[] bytes) { files.put(OverrideFiles.filename(id, version), bytes); }
        public Long installedVersion(String id) {
            if (forbidDestination) throw new AssertionError("Destination read despite exact source override");
            versionReads++; return versions.get(id);
        }
        public byte[] savedPem(String id, long version) { return files.get(OverrideFiles.filename(id, version)); }
        public byte[] installedPem(String id) { installedReads++; return installed.get(id); }
    }
    public static void main(String[] args) throws Exception {
        destinationOverrideForSourceQuery(); sourcePrecedence(); wrongSourceVersion(); installedDestination();
        absentDestinationWithSavedPem(); unrelatedPackage(); invalidOverrides(); missingIdentity(); multipleSigners();
        System.out.println("9 shared certificate lookup checks passed");
    }
    private static CertificateLookup.Result resolve(Backend backend) throws Exception {
        return CertificateLookup.resolve(backend, SOURCE, SOURCE_VERSION, TARGET);
    }
    private static void destinationOverrideForSourceQuery() throws Exception {
        Backend backend = new Backend(); byte[] pem = fixture("signer.crt");
        backend.versions.put(TARGET, TARGET_VERSION); backend.save(TARGET, TARGET_VERSION, pem);
        CertificateLookup.Result result = resolve(backend);
        check(result.packageName.equals(TARGET) && result.version == TARGET_VERSION);
        check(result.origin.equals("destination override")); check(Arrays.equals(result.pem, pem));
        // Package-query PEM and token hash must describe the identical certificate.
        check(CertificateLookup.tokenSigner(result).equals(TokenRequestSwap.hash(OverrideFiles.certificate(result.pem).getEncoded())));
    }
    private static void sourcePrecedence() throws Exception {
        Backend backend = new Backend(); backend.forbidDestination = true;
        backend.save(SOURCE, SOURCE_VERSION, fixture("second-signer.crt"));
        CertificateLookup.Result result = resolve(backend);
        check(result.packageName.equals(SOURCE) && result.origin.equals("source override"));
        check(backend.versionReads == 0 && backend.installedReads == 0);
    }
    private static void wrongSourceVersion() throws Exception {
        Backend backend = new Backend(); backend.save(SOURCE, SOURCE_VERSION + 1, fixture("second-signer.crt"));
        backend.versions.put(TARGET, TARGET_VERSION); backend.save(TARGET, TARGET_VERSION, fixture("signer.crt"));
        check(resolve(backend).packageName.equals(TARGET));
    }
    private static void installedDestination() throws Exception {
        Backend backend = new Backend(); backend.versions.put(TARGET, TARGET_VERSION);
        byte[] current = fixture("signer.crt"); backend.installed.put(TARGET, current);
        backend.save(TARGET, SOURCE_VERSION, fixture("second-signer.crt")); // Wrong target version is ignored.
        CertificateLookup.Result result = resolve(backend);
        check(result.origin.equals("destination installed signer") && Arrays.equals(result.pem, current));
    }
    private static void absentDestinationWithSavedPem() throws Exception {
        Backend backend = new Backend(); backend.save(TARGET, SOURCE_VERSION, fixture("signer.crt"));
        CertificateLookup.Result result = resolve(backend);
        check(result.packageName.equals(TARGET) && result.version == SOURCE_VERSION && backend.installedReads == 0);
    }
    private static void unrelatedPackage() throws Exception {
        Backend backend = new Backend(); backend.forbidDestination = true;
        check(CertificateLookup.resolve(backend, SOURCE, SOURCE_VERSION, SOURCE) == null);
        check(backend.versionReads == 0);
    }
    private static void invalidOverrides() throws Exception {
        Backend backend = new Backend(); backend.save(SOURCE, SOURCE_VERSION, new byte[]{1, 2});
        backend.versions.put(TARGET, TARGET_VERSION); backend.installed.put(TARGET, fixture("signer.crt"));
        try { resolve(backend); throw new AssertionError("Invalid source ignored"); } catch (IllegalArgumentException expected) {}
        check(backend.versionReads == 0);
        backend.files.clear(); backend.save(TARGET, TARGET_VERSION, new byte[]{1, 2});
        try { resolve(backend); throw new AssertionError("Invalid destination ignored"); } catch (IllegalArgumentException expected) {}
        check(backend.installedReads == 0);
    }
    private static void missingIdentity() throws Exception {
        check(resolve(new Backend()) == null);
        try { CertificateLookup.tokenSigner(null); throw new AssertionError("Missing signer accepted"); }
        catch (IllegalArgumentException expected) {}
    }
    private static void multipleSigners() throws Exception {
        Backend backend = new Backend();
        byte[] pem = OverrideFiles.pem(Arrays.asList(OverrideFiles.certificate(fixture("signer.crt")),
                OverrideFiles.certificate(fixture("second-signer.crt"))));
        backend.save(TARGET, SOURCE_VERSION, pem);
        CertificateLookup.Result result = resolve(backend);
        check(OverrideFiles.certificates(result.pem).size() == 2);
        try { CertificateLookup.tokenSigner(result); throw new AssertionError("Ambiguous token signer accepted"); }
        catch (IllegalArgumentException expected) {}
    }
}
