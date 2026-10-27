package com.example.signinginfooverride;

public final class EntitlementScopeTest {
    private static final String A = "org.example.source", B = "org.example.destination", C = "org.example.third";
    private static void check(boolean value) { if (!value) throw new AssertionError(); }

    public static void main(String[] args) throws Exception {
        outsideScope(); supportedBuilder(); nestedUnrelatedBuilder(); nestedEntitlements(); singlePass(); threadAndExceptionCleanup(); earlyLibraryLookup(); crossAdapterNoChain();
        System.out.println("8 entitlement and library scope checks passed");
    }

    private static void outsideScope() {
        EntitlementScope scope = new EntitlementScope();
        check(scope.current() == null);
        scope.enter(false); check(scope.current() == null); scope.exit();
        check(scope.current() == null);
    }

    private static void supportedBuilder() {
        EntitlementScope scope = new EntitlementScope(); scope.enter(true);
        check(scope.current().resolve(A, B).equals(B));
        scope.exit(); check(scope.current() == null);
    }

    private static void nestedUnrelatedBuilder() {
        EntitlementScope scope = new EntitlementScope(); scope.enter(true);
        EntitlementScope.Frame outer = scope.current(); scope.enter(false);
        check(scope.current() == null); scope.exit();
        check(scope.current() == outer); check(outer.resolve(A, B).equals(B)); scope.exit();
    }

    private static void nestedEntitlements() {
        EntitlementScope scope = new EntitlementScope(); scope.enter(true);
        EntitlementScope.Frame outer = scope.current(); scope.enter(true);
        check(scope.current() != outer); check(scope.current().resolve(B, C).equals(C)); scope.exit();
        check(scope.current() == outer); check(scope.current().resolve(A, B).equals(B)); scope.exit();
    }

    private static void singlePass() {
        EntitlementScope scope = new EntitlementScope(); scope.enter(true);
        EntitlementScope.Frame frame = scope.current();
        check(frame.resolve(A, B).equals(B));
        check(frame.resolve(B, C).equals(B)); // The already-presented ID is never chained.
        check(frame.resolve(A, C).equals(B)); // A request retains its initial resolved alias.
        scope.exit();
    }

    private static void earlyLibraryLookup() {
        EntitlementScope scope = new EntitlementScope();
        check(scope.libraryPackage(A, B).equals(B)); // Pre-token resolution has no entitlement builder frame.
        check(scope.libraryPackage(A, A).equals(A));
    }

    private static void crossAdapterNoChain() {
        EntitlementScope scope = new EntitlementScope(); scope.enter(true);
        check(scope.current().resolve(A, B).equals(B));
        check(scope.libraryPackage(B, C).equals(B)); // Do not remap an already-selected destination.
        check(scope.libraryPackage(A, C).equals(B)); // Retain the original frame's resolved alias.
        scope.exit();
        check(scope.libraryPackage(B, C).equals(C)); // A new independent request may use B as source.
    }

    private static void threadAndExceptionCleanup() throws Exception {
        EntitlementScope scope = new EntitlementScope(); scope.enter(true);
        final boolean[] isolated = {false};
        Thread thread = new Thread(() -> {
            isolated[0] = scope.current() == null;
            scope.enter(false); scope.exit();
        });
        thread.start(); thread.join(); check(isolated[0]); check(scope.current() != null);
        scope.exit();
        try {
            scope.enter(true);
            try { throw new IllegalStateException("simulated builder failure"); }
            finally { scope.exit(); }
        } catch (IllegalStateException expected) {}
        check(scope.current() == null);
    }
}
