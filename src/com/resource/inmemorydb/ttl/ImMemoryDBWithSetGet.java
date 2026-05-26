package com.resource.inmemorydb.ttl;

import com.resouce.taskmanagement.Task;

import java.net.CookieHandler;
import java.util.*;
import java.util.stream.Collectors;

public class ImMemoryDBWithSetGet {
    // key: {fields, values} -> Map<String, Map<String, String>>
    private  final Map<String, Map<String, String>> db = new HashMap<>();
    public void set(String key, String field, String value) {
        db.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
    }

    public String get(String key, String field) {
//        Map<String,String> f2v = db.get(key);
//        if (f2v == null) return null;
//        return f2v.get(field);
        return Optional.ofNullable(db.get(key))
                .map(f2v -> f2v.get(field)).orElse(null);
    }

    public List<String> scan(String key) {
//        Map<String, String> f2v = db.get(key);
//        if (f2v == null) return Collections.emptyList();
//
//        List<String> rets = new ArrayList<>();
//        for (String field : f2v.keySet()) {
//            rets.add(field + "(" + f2v.get(field) + ")");
//        }
//        Collections.sort(rets);
//        return rets;
        return Optional.ofNullable(db.get(key))
                .map(f2v -> f2v.entrySet().stream()
                        .map(e -> e.getKey() + "(" + e.getValue() + ")")
                        .sorted()
                        .toList())
                .orElse(null);
    }

    public List<String> scanPrefix(String key, String prefix) {
//        Map<String, String> f2v = db.get(key);
//        if (f2v == null) return Collections.emptyList();
//
//        List<String> rets = new ArrayList<>();
//        int size = prefix.length();
//        for (String field : f2v.keySet()) {
//            //String value = f2v.get(field);
//            if (!field.substring(0, size).equals(prefix)) continue;
//            rets.add(field + "(" + f2v.get(field) + ")");
//        }
//        Collections.sort(rets);
//        return rets;
        return Optional.ofNullable(db.get(key))
                .map(f2v -> f2v.entrySet().stream()
                        .filter(e -> e.getKey().substring(0, prefix.length()).equals(prefix))
                        .map(e -> e.getKey() + "(" + e.getValue() + ")")
                        .sorted()
                        .toList())
                .orElse(null);
    }

    public static void main(String[] args) {

        ImMemoryDBWithSetGet db =
                new ImMemoryDBWithSetGet();

//        db.set(
//                "user1",
//                "name",
//                "Alice");
//
//        db.set(
//                "user1",
//                "age",
//                "25");
//
//        db.set(
//                "user2",
//                "city",
//                "Seattle");
//
//        System.out.println(
//                db.get("user1", "name"));
//
//        System.out.println(
//                db.get("user1", "age"));
//
//        System.out.println(
//                db.get("user2", "city"));
//
//        System.out.println(
//                db.get("user2", "country"));
        db.set(
                "user1",
                "name",
                "Alice");

        db.set(
                "user1",
                "age",
                "25");

        db.set(
                "user1",
                "address",
                "Seattle");

        db.set(
                "user1",
                "avatar",
                "img.png");

        System.out.println(
                db.scan("user1"));

        System.out.println(
                db.scanPrefix(
                        "user1",
                        "a"));
    }
}
