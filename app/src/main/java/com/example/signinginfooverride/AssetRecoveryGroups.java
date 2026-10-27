package com.example.signinginfooverride;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Splits a local recovery grouping map without changing its task records. */
final class AssetRecoveryGroups {
    private final Set<Object> records = Collections.newSetFromMap(new IdentityHashMap<>());
    private Map<Object, Object> grouping;
    private Object packageKey, bucket;

    void batch(Collection<?> values) {
        records.clear(); records.addAll(values);
        grouping = null; packageKey = null; bucket = null;
    }

    @SuppressWarnings("unchecked")
    void lookup(Map<?, ?> map, Object key, Object value) {
        grouping = (Map<Object, Object>) map;
        packageKey = key; bucket = value;
    }

    boolean accepts(Object list, Object record) {
        return records.contains(record) && grouping != null && bucket == list
                && grouping.get(packageKey) == list && list instanceof List<?>;
    }

    Object destination(Object list, Object record, String packageName, String requestId) {
        if (!accepts(list, record) || !Objects.equals(packageKey, packageName) || requestId == null) return list;
        Key key = new Key(packageName, requestId);
        Object result = grouping.get(key);
        if (result == null) { result = new ArrayList<>(); grouping.put(key, result); }
        // The original get-or-create helper inserted this bucket. Do not leave an empty group behind.
        if (((List<?>) list).isEmpty()) grouping.remove(packageKey);
        return result;
    }

    private static final class Key {
        final String packageName, requestId;
        Key(String packageName, String requestId) { this.packageName = packageName; this.requestId = requestId; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return packageName.equals(key.packageName) && requestId.equals(key.requestId);
        }
        @Override public int hashCode() { return Objects.hash(packageName, requestId); }
    }
}
