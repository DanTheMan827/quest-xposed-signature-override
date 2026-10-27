package com.example.signinginfooverride;

public final class AssetRouteTest {
    private static final String SOURCE = "org.example.copy", TARGET = "org.example.original";
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        AssetRoute route = new AssetRoute(SOURCE, TARGET);
        check(route.storagePackage(TARGET).equals(SOURCE));
        check(route.storagePackage(SOURCE).equals(SOURCE));
        check(route.storagePackage("org.example.unrelated").equals("org.example.unrelated"));
        check(route.destination.equals(TARGET)); // Network identity is immutable.
        check(AssetRoute.decode(route.encode()).source.equals(SOURCE));
        check(AssetRoute.decode(null) == null);
        for (String invalid : new String[]{"", SOURCE, SOURCE + "\n" + SOURCE,
                SOURCE + "\n" + TARGET + "\nextra", "../escape\n" + TARGET}) {
            try { AssetRoute.decode(invalid); throw new AssertionError("Invalid route accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        AssetRoute otherCopy = new AssetRoute("org.example.secondcopy", TARGET);
        AssetRoute.Scope scope = new AssetRoute.Scope();
        check(scope.current() == null);
        scope.enter(route);
        scope.enter(otherCopy); check(scope.current().storagePackage(TARGET).equals(otherCopy.source));
        scope.exit(); check(scope.current() == route);
        scope.enter(null); check(scope.current() == null); scope.exit(); check(scope.current() == route);
        final boolean[] isolated = {false};
        Thread thread = new Thread(() -> {
            isolated[0] = scope.current() == null;
            scope.enter(otherCopy); scope.exit(); check(scope.current() == null);
        });
        thread.start(); thread.join(); check(isolated[0]);
        scope.exit();
        try {
            scope.enter(route);
            try { throw new IllegalStateException(); } finally { scope.exit(); }
        } catch (IllegalStateException expected) { }
        check(scope.current() == null);
        EntitlementScope identities = new EntitlementScope(); identities.enter(true);
        check(identities.current().assetRoute() == null);
        check(identities.current().resolve(SOURCE, TARGET).equals(TARGET));
        check(identities.current().assetRoute().source.equals(SOURCE));
        check(identities.current().resolve(TARGET, "org.example.third").equals(TARGET));
        check(identities.current().assetRoute().destination.equals(TARGET));
        identities.enter(false); check(identities.current() == null); identities.exit(); identities.exit();
        check(AssetRoute.assetRequest("StartAsset", true));
        check(!AssetRoute.assetRequest("StartAsset", false));
        check(AssetRoute.assetRequest("UninstallAsset", false));
        check(AssetRoute.assetRequest("CancelAsset", false));
        for (String action : new String[]{"Start", "Uninstall", "Cancel", "ClearSandbox", "StartShaderUpdate", "Resume", null})
            check(!AssetRoute.assetRequest(action, true));
        // The receiver has no caller frame: it must recover each real owner from the transmitted pair.
        String firstMessage = route.encode(), secondMessage = otherCopy.encode();
        final boolean[] transferred = {false};
        Thread receiver = new Thread(() -> {
            check(scope.current() == null);
            AssetRoute firstRoute = AssetRoute.decode(firstMessage), secondRoute = AssetRoute.decode(secondMessage);
            check(firstRoute.destination.equals(secondRoute.destination));
            check(firstRoute.storagePackage(TARGET).equals(SOURCE));
            check(secondRoute.storagePackage(TARGET).equals(otherCopy.source));
            transferred[0] = true;
        });
        receiver.start(); receiver.join(); check(transferred[0]);
        System.out.println("8 asset storage routing checks passed");
    }
}
