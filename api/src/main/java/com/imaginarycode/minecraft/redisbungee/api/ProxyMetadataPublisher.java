package com.imaginarycode.minecraft.redisbungee.api;

import redis.clients.jedis.UnifiedJedis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A single-key, expiring advertisement independent of the RedisBungee stream. */
public final class ProxyMetadataPublisher {
    public static final int TTL_SECONDS = 30;

    // Only the initial claim can replace another launch. Renewals are fenced by
    // owner, including after reconnect; DEL + HSET removes obsolete parameters.
    private static final String PUBLISH = """
            local owner = redis.call('HGET', KEYS[1], 'owner')
            if ARGV[1] ~= 'claim' and owner and owner ~= ARGV[2] then return 0 end
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'owner', ARGV[2], 'schema', '1')
            for i = 4, #ARGV, 2 do redis.call('HSET', KEYS[1], ARGV[i], ARGV[i+1]) end
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """;
    private static final String REMOVE = """
            if redis.call('HGET', KEYS[1], 'owner') == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private final UnifiedJedis jedis;
    private final String key;
    private final String owner = UUID.randomUUID().toString();
    private final Map<String, String> metadata;
    private boolean claimed;
    private boolean superseded;
    private boolean closed;

    public ProxyMetadataPublisher(UnifiedJedis jedis, String networkId, String proxyId,
                                  Map<String, String> metadata) {
        this.jedis = jedis;
        // Encode braces/colons/percent in IDs to avoid ambiguous scopes and
        // Redis Cluster hash tags. Ordinary XMine IDs keep their familiar form.
        this.key = "redisbungee::" + escape(networkId) + "::proxies::" + escape(proxyId) + "::metadata";
        this.metadata = Map.copyOf(metadata);
    }

    private static String escape(String id) {
        return id.replace("%", "%25").replace(":", "%3A")
                .replace("{", "%7B").replace("}", "%7D");
    }

    public String owner() {
        return owner;
    }

    public synchronized void publish() {
        if (closed || superseded) return;
        List<String> args = new ArrayList<>();
        args.add(claimed ? "renew" : "claim");
        // Set before I/O: an ambiguous connection failure must never retry a
        // takeover that could replace a newer process's successful claim.
        claimed = true;
        args.add(owner);
        args.add(Integer.toString(TTL_SECONDS));
        metadata.forEach((name, value) -> {
            args.add("param:" + name);
            args.add(value);
        });
        superseded = Long.valueOf(0).equals(jedis.eval(PUBLISH, List.of(key), args));
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        jedis.eval(REMOVE, List.of(key), List.of(owner));
    }
}
