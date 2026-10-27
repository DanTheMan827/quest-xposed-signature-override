package com.example.signinginfooverride;

import java.util.ArrayDeque;
import java.util.Deque;

/** A request's platform identity and local storage owner are independent. */
final class AssetRoute {
    final String source, destination;

    AssetRoute(String source, String destination) {
        PackageSwaps.pair(source, destination);
        this.source = source;
        this.destination = destination;
    }

    String storagePackage(String packageName) {
        return destination.equals(packageName) ? source : packageName;
    }

    String encode() { return source + "\n" + destination; }

    static boolean assetRequest(String action, boolean installAsset) {
        return ("StartAsset".equals(action) && installAsset)
                || "UninstallAsset".equals(action) || "CancelAsset".equals(action);
    }

    static AssetRoute decode(String value) {
        if (value == null) return null;
        String[] parts = value.split("\n", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid asset route");
        return new AssetRoute(parts[0], parts[1]);
    }

    /** Null frames deliberately suppress an enclosing download's route. */
    static final class Scope {
        private final ThreadLocal<Deque<Frame>> frames = new ThreadLocal<>();
        private static final class Frame {
            final AssetRoute route;
            Frame(AssetRoute route) { this.route = route; }
        }
        void enter(AssetRoute route) {
            Deque<Frame> stack = frames.get();
            if (stack == null) { stack = new ArrayDeque<>(); frames.set(stack); }
            stack.push(new Frame(route));
        }
        AssetRoute current() {
            Deque<Frame> stack = frames.get();
            return stack == null || stack.isEmpty() ? null : stack.peek().route;
        }
        void exit() {
            Deque<Frame> stack = frames.get();
            if (stack == null) return;
            if (!stack.isEmpty()) stack.pop();
            if (stack.isEmpty()) frames.remove();
        }
    }
}
