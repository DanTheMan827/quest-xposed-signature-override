package com.example.signinginfooverride;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AssetRecoveryGroupsTest {
    private static final String PACKAGE = "org.example.destination";
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    @SuppressWarnings("unchecked")
    private static List<Object> add(AssetRecoveryGroups groups, Map<Object, Object> map, Object record, String request) {
        List<Object> bucket = (List<Object>) map.get(PACKAGE);
        if (bucket == null) { bucket = new ArrayList<>(); map.put(PACKAGE, bucket); }
        groups.lookup(map, PACKAGE, bucket);
        List<Object> target = (List<Object>) groups.destination(bucket, record, PACKAGE, request);
        target.add(record); return target;
    }
    public static void main(String[] args) {
        Object a = new Object(), b = new Object(), c = new Object(), ordinary = new Object();
        AssetRecoveryGroups groups = new AssetRecoveryGroups();
        groups.batch(Arrays.asList(a, b, c, ordinary));
        Map<Object, Object> map = new LinkedHashMap<>();
        List<Object> first = add(groups, map, a, "request-a");
        check(map.size() == 1 && !map.containsKey(PACKAGE)); // No empty default group.
        check(add(groups, map, b, "request-a") == first && first.equals(Arrays.asList(a, b)));
        List<Object> second = add(groups, map, c, "request-b");
        check(second != first && map.size() == 2 && second.get(0) == c);
        List<Object> normal = add(groups, map, ordinary, null);
        check(map.get(PACKAGE) == normal && normal.size() == 1 && map.size() == 3);
        check(add(groups, map, a, "request-a") == first && map.get(PACKAGE) == normal);
        check(!groups.accepts(normal, new Object()));
        Map<Object, Object> unrelated = new LinkedHashMap<>(); List<Object> other = new ArrayList<>();
        unrelated.put("org.example.other", other); groups.lookup(unrelated, "org.example.other", other);
        check(groups.destination(other, a, PACKAGE, "request-a") == other && unrelated.size() == 1);
        groups.batch(Arrays.asList(new Object()));
        check(!groups.accepts(other, a)); // A new database batch cannot reuse prior records.
        Map<Object, Object> ordinaryFirst = new LinkedHashMap<>(); groups.batch(Arrays.asList(a, ordinary));
        List<Object> original = add(groups, ordinaryFirst, ordinary, null);
        List<Object> routed = add(groups, ordinaryFirst, a, "request-a");
        check(original != routed && ordinaryFirst.get(PACKAGE) == original && original.get(0) == ordinary);
        System.out.println("8 asset recovery grouping checks passed");
    }
}
