package com.example.signinginfooverride;

import java.util.ArrayDeque;
import java.util.Deque;

/** Per-thread call frames prevent a package alias from affecting unrelated service calls. */
final class EntitlementScope {
    static final class Frame {
        final boolean eligible;
        private String source, target;
        Frame(boolean eligible) { this.eligible = eligible; }

        AssetRoute assetRoute() {
            return source == null || source.equals(target) ? null : new AssetRoute(source, target);
        }

        String resolve(String original, String presented) {
            if (!eligible) return original;
            PackageSwaps.packageName(original);
            PackageSwaps.packageName(presented);
            if (source == null) { source = original; target = presented; }
            return source.equals(original) ? target : original;
        }
    }

    private final ThreadLocal<Deque<Frame>> frames = new ThreadLocal<>();

    void enter(boolean eligible) {
        Deque<Frame> stack = frames.get();
        if (stack == null) { stack = new ArrayDeque<>(); frames.set(stack); }
        stack.push(new Frame(eligible));
    }

    Frame current() {
        Deque<Frame> stack = frames.get();
        return stack == null || stack.isEmpty() || !stack.peek().eligible ? null : stack.peek();
    }

    String libraryPackage(String original, String presented) {
        PackageSwaps.packageName(original); PackageSwaps.packageName(presented);
        Frame frame = current();
        return frame == null ? presented : frame.resolve(original, presented);
    }

    void exit() {
        Deque<Frame> stack = frames.get();
        if (stack == null) return;
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) frames.remove();
    }
}
