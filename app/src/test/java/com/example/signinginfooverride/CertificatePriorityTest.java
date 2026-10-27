package com.example.signinginfooverride;

public final class CertificatePriorityTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        sourceWins(); destinationFallback(); missingIdentity(); invalidExactSource();
        System.out.println("4 certificate priority checks passed");
    }
    private static void sourceWins() throws Exception {
        byte[] source = {1, 2, 3};
        byte[] chosen = CertificatePriority.resolve(() -> source, () -> { throw new AssertionError("Destination must not be read"); });
        check(chosen == source);
    }
    private static void destinationFallback() throws Exception {
        byte[] destination = {4, 5, 6};
        check(CertificatePriority.resolve(() -> null, () -> destination) == destination);
    }
    private static void missingIdentity() throws Exception {
        try { CertificatePriority.resolve(() -> null, () -> null); throw new AssertionError("Missing identity accepted"); }
        catch (IllegalArgumentException expected) {}
    }
    private static void invalidExactSource() throws Exception {
        try {
            CertificatePriority.resolve(() -> { throw new java.io.IOException("Unreadable exact override"); },
                    () -> { throw new AssertionError("Invalid exact override must not silently select another signer"); });
            throw new AssertionError("Invalid override accepted");
        } catch (java.io.IOException expected) {}
    }
}
