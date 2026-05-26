package com.resource.inmemorydb.ttl;

import jdk.dynalink.beans.StaticClass;

import java.util.*;

public class ImMemoryDBWithTTL {
    // key -> filed(s) -> value(val, timestamp, ttl)
    // expiration conditons:
    // expire = timestamp + ttl
    // now > expire
    /* value object*/
    // -1 mean never expired
    // https://www.1point3acres.com/bbs/thread-1128891-1-1.html
    /* *实现一个 inMemoryDB, 就是 {key: {field1: value1, field2: value2....}}的格式， 有 get/set 功能就可以
    添加 scan/scanPrefix 的功能，比如: scan(key) -> [field1(value1), field2(value2)....] ; scanPrefix(key, perfix) -> 只返回field 含有给定prefix 的
    添加一个 TTL 功能， get/set/scan/scanPrefix，都加上一个timestamp和 ttl，只返回不expired
    添加backup / restore 功能，backup时候返回一个数值表示当前有多少个未expired的field；restore 就回复上一次back的数值；这两个函数都带 timestamp，表示是什么时候发生的*/
    static class ValueTTL {
        String val;
        long createdAt;
        long ttl;

        public ValueTTL(String val, long createdAt, long ttl) {
            this.val = val;
            this.createdAt = createdAt;
            this.ttl = ttl;
        }

        public boolean isExpired(long now) {
            if(ttl < 0) return false;
            return now >= createdAt + ttl;
        }
    }
    static class SnapshotTTL {

        long backuptime;
        Map<String, Map<String, ValueTTL>> backdb = new HashMap<>();
        public SnapshotTTL(long backuptime, Map<String, Map<String, ValueTTL>> backdb) {
            this.backuptime = backuptime;
            this.backdb = backdb;
        }
    }
    private  final Map<String, Map<String, ValueTTL>> db = new HashMap<>(); // time com -> sorted map as value
    private  final TreeMap<Long, SnapshotTTL> snapshots = new TreeMap<>(); // backup time(snapshot) -> all valid unexpired data
    public void set(String key, String field, String value, long timestamp, long ttl) {
        db.computeIfAbsent(key, k -> new HashMap<>()).put(field, new ValueTTL(value, timestamp, ttl));
    }

    public String get(String key, String field, long now) {
        Map<String,ValueTTL> f2v = db.get(key);
        if (f2v == null) return null;
        ValueTTL v = f2v.get(field);
        if (v == null) {
            return null;
        }
        if (v.isExpired(now)) {
            return null;
        }

        return v.val;
//        return Optional.ofNullable(db.get(key))
//                .map(f2v -> f2v.get(field))
//                .filter(e -> e.isExpired(now)).orElse(null);
    }

    public List<String> scan(String key, long now) {
        Map<String, ValueTTL> f2v = db.get(key);
        if (f2v == null) return Collections.emptyList();

        List<String> rets = new ArrayList<>();
        for (String field : f2v.keySet()) {
            ValueTTL v = f2v.get(field);
            if (v == null || v.isExpired(now)) continue;
            rets.add(field + "(" + v.val + ")");
        }
        Collections.sort(rets);
        return rets;
//        return Optional.ofNullable(db.get(key))
//                .map(f2v -> f2v.entrySet().stream()
//                        .map(e -> e.getKey() + "(" + e.getValue() + ")")
//                        .sorted()
//                        .toList())
//                .orElse(null);
    }

    public List<String> scanPrefix(String key, String prefix, long now) {
        Map<String, ValueTTL> f2v = db.get(key);
        if (f2v == null) return Collections.emptyList();

        List<String> rets = new ArrayList<>();
        int size = prefix.length();
        for (String field : f2v.keySet()) {
            ValueTTL v = f2v.get(field);
            if (v == null || v.isExpired(now)) continue;
            if (!field.substring(0, size).equals(prefix)) continue;
            rets.add(field + "(" + v.val + ")");
        }
        Collections.sort(rets);
        return rets;
//        return Optional.ofNullable(db.get(key))
//                .map(f2v -> f2v.entrySet().stream()
//                        .filter(e -> e.getKey().substring(0, prefix.length()).equals(prefix))
//                        .map(e -> e.getKey() + "(" + e.getValue() + ")")
//                        .sorted()
//                        .toList())
//                .orElse(null);
    }

    // snapshot solution for backup and restore
    // backup(long currenttime) store all unexpired data into temp db
    // treemap for quick to search the last recent db
    public int backup(long now) {
        Map<String, Map<String, ValueTTL>> tempdb = new HashMap<>();

        int activecount = 0;
        for (Map.Entry<String, Map<String, ValueTTL>> entry : db.entrySet()) {
            // k : entry.getKey(), v: entry.getValue()
            // key is key, value is db data with field and value
            String key = entry.getKey();
            Map<String, ValueTTL> f2v = entry.getValue();
            for (String field : f2v.keySet()) {
                ValueTTL v = f2v.get(field);
                if (v == null || v.isExpired(now)) continue;
                activecount++;
                tempdb.computeIfAbsent(key, k -> new HashMap<>()).put(field, v);
            }
            //tempdb
        }

        snapshots.put(now, new SnapshotTTL(now, tempdb));
        return activecount;
    }

    public void restore(long now) {
        Map.Entry<Long, SnapshotTTL> entry = snapshots.floorEntry(now);

        if (entry == null) return;
        SnapshotTTL snapshotdb = entry.getValue();

        db.clear();
        // deep copy restore
        for (Map.Entry<String, Map<String, ValueTTL>> key2field : snapshotdb.backdb.entrySet()) {
            String key = key2field.getKey();
            Map<String, ValueTTL> lastf2v = new HashMap<>();
            Map<String, ValueTTL> f2v = key2field.getValue();
            for (String field : f2v.keySet()) {
                ValueTTL v = f2v.get(field);
                if (v == null || v.isExpired(now)) continue;
                lastf2v.put(field, new ValueTTL(v.val, v.createdAt, v.ttl));
            }
            db.put(key, lastf2v); // key: value is map
        }
    }

    public static void main(String[] args) {

//        ImMemoryDBWithTTL db =
//                new ImMemoryDBWithTTL();
//
//        /**
//         * never expire
//         */
//        db.set(
//                "user1",
//                "name",
//                "Alice",
//                1000,
//                -1);
//
//        /**
//         * expire at:
//         * 1000 + 5000 = 6000
//         */
//        db.set(
//                "user1",
//                "session",
//                "xyz",
//                1000,
//                5000);
//
//        System.out.println(
//                db.get(
//                        "user1",
//                        "name",
//                        2000));
//
//        System.out.println(
//                db.get(
//                        "user1",
//                        "session",
//                        2000));
//
//        /**
//         * expired
//         */
//        System.out.println(
//                db.get(
//                        "user1",
//                        "session",
//                        7000));
//
//        System.out.println(
//                db.scan(
//                        "user1",
//                        2000));
//
//        System.out.println(
//                db.scan(
//                        "user1",
//                        7000));
//
//        System.out.println(
//                db.scanPrefix(
//                        "user1",
//                        "s",
//                        2000));
//
//        System.out.println(
//                db.scanPrefix(
//                        "user1",
//                        "s",
//                        7000));

        ImMemoryDBWithTTL db =
                new ImMemoryDBWithTTL();

        db.set(
                "user1",
                "name",
                "Alice",
                1000,
                -1);

        db.set(
                "user1",
                "session",
                "xyz",
                1000,
                5000);

        System.out.println(
                db.scan(
                        "user1",
                        2000));

        /**
         * backup at 3000
         */
        int count =
                db.backup(3000);

        System.out.println(
                "backup count = "
                        + count);

        /**
         * session expired
         */
        System.out.println(
                db.scan(
                        "user1",
                        7000));

        /**
         * restore snapshot at 3000
         */
        db.restore(3000);

        System.out.println(
                db.scan(
                        "user1",
                        4000));

    }
}
