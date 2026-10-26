package com.example.signinginfooverride;

import java.io.*;
import java.nio.file.Files;
import java.security.cert.X509Certificate;

public class OverrideFilesTest {
    public static void main(String[] args) throws Exception {
        OverrideFilesTest tests = new OverrideFilesTest();
        tests.exactVersionUsesLongNotDisplayName();
        tests.rejectsAmbiguousOrUnsafeNames();
        tests.pemRoundTripPreservesCertificateAndFingerprint();
        tests.rejectsDuplicateCertificatesAndGarbage();
        tests.multipleCurrentSignersRoundTrip();
        tests.boundsReads();
        tests.rejectsSymlinkOutsideDirectory();
        System.out.println("7 core checks passed");
    }
    private static void fail() { throw new AssertionError(); }
    private static void fail(String message) { throw new AssertionError(message); }
    private static void assertEquals(Object a, Object b) { if (!a.equals(b)) fail(a + " != " + b); }
    private static void assertEquals(long a, long b) { if (a != b) fail(a + " != " + b); }
    private static void assertArrayEquals(byte[] a, byte[] b) { if (!java.util.Arrays.equals(a, b)) fail("Bytes differ"); }

    private byte[] fixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/signer.crt")) { return OverrideFiles.read(in); }
    }

    public void exactVersionUsesLongNotDisplayName() {
        OverrideFiles.Key key = OverrideFiles.parse("org.example.application+4294967297.pem");
        assertEquals("org.example.application", key.packageName);
        assertEquals(4294967297L, key.version);
        assertEquals("org.example.application+4294967297.pem", key.filename());
        assertEquals(0, OverrideFiles.parse("org.example.application+0.pem").version);
    }

    public void rejectsAmbiguousOrUnsafeNames() {
        for (String name : new String[]{"../org.example+1.pem", "org.example+-1.pem", "org.example+01.pem",
                "org.example+1.2.pem", "org.example+*.pem", "org.example+9223372036854775808.pem",
                "org.example+1.pem.bak", "org.example;id+1.pem", "org.example+1.PEM"}) {
            try { OverrideFiles.parse(name); fail(name); } catch (IllegalArgumentException expected) {}
        }
    }

    public void pemRoundTripPreservesCertificateAndFingerprint() throws Exception {
        X509Certificate certificate = OverrideFiles.certificate(fixture());
        X509Certificate reloaded = OverrideFiles.certificate(OverrideFiles.pem(certificate));
        assertArrayEquals(certificate.getEncoded(), reloaded.getEncoded());
        assertEquals(OverrideFiles.fingerprint(certificate), OverrideFiles.fingerprint(reloaded));
        assertEquals(95, OverrideFiles.fingerprint(certificate).length());
    }

    public void rejectsDuplicateCertificatesAndGarbage() throws Exception {
        byte[] pem = fixture();
        ByteArrayOutputStream two = new ByteArrayOutputStream(); two.write(pem); two.write(pem);
        for (byte[] invalid : new byte[][]{two.toByteArray(), "not a certificate".getBytes("US-ASCII"),
                (new String(pem, "US-ASCII") + "PRIVATE KEY").getBytes("US-ASCII")}) {
            try { OverrideFiles.certificates(invalid); fail("Accepted invalid PEM"); } catch (Exception expected) {}
        }
    }

    public void multipleCurrentSignersRoundTrip() throws Exception {
        X509Certificate first = OverrideFiles.certificate(fixture());
        X509Certificate second;
        try (InputStream in = getClass().getResourceAsStream("/second-signer.crt")) {
            second = OverrideFiles.certificate(OverrideFiles.read(in));
        }
        java.util.List<X509Certificate> signers = java.util.Arrays.asList(first, second);
        java.util.List<X509Certificate> reloaded = OverrideFiles.certificates(OverrideFiles.pem(signers));
        assertEquals(2, reloaded.size());
        assertArrayEquals(first.getEncoded(), reloaded.get(0).getEncoded());
        assertArrayEquals(second.getEncoded(), reloaded.get(1).getEncoded());
    }

    public void boundsReads() throws Exception {
        assertEquals(65536, OverrideFiles.read(new ByteArrayInputStream(new byte[65536])).length);
        try { OverrideFiles.read(new ByteArrayInputStream(new byte[65537])); fail(); } catch (IOException expected) {}
    }

    public void rejectsSymlinkOutsideDirectory() throws Exception {
        java.nio.file.Path base = Files.createTempDirectory("override-test-");
        java.nio.file.Path outside = Files.createTempFile("outside-", ".crt");
        java.nio.file.Path link = base.resolve("org.example+1.pem");
        try {
            Files.createSymbolicLink(link, outside);
            try { OverrideFiles.resolve(base.toFile(), link.getFileName().toString()); fail(); }
            catch (IOException expected) {}
        } finally { Files.deleteIfExists(link); Files.deleteIfExists(base); Files.deleteIfExists(outside); }
    }
}
