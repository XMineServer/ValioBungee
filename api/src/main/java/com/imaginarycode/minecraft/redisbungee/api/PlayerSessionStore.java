package com.imaginarycode.minecraft.redisbungee.api;

import redis.clients.jedis.UnifiedJedis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keeps disconnect cleanup tied to the connection that wrote the player data. */
public final class PlayerSessionStore {
    private static final String REMOVE = """
            if redis.call('HGET', KEYS[1], 'proxy-owner') ~= ARGV[1]
                or redis.call('HGET', KEYS[1], 'session') ~= ARGV[2] then return 0 end
            redis.call('HSET', KEYS[1], 'last-online', ARGV[3])
            redis.call('HDEL', KEYS[1], 'server', 'proxy', 'ip', 'proxy-owner', 'session')
            return 1
            """;
    private final UnifiedJedis jedis;
    private final String network;
    private final String owner;
    private final Map<Object, String> sessions = new HashMap<>();
    private final Map<String, Removal> pending = new LinkedHashMap<>();

    private record Removal(UUID uuid, String session, long disconnectedAt) {}
    public record Cleanup(UUID uuid, boolean owned) {}

    public PlayerSessionStore(UnifiedJedis jedis, String network, String owner) {
        this.jedis = jedis;
        this.network = network;
        this.owner = owner;
    }

    private String key(UUID uuid) {
        return "redis-bungee::" + network + "::player::" + uuid + "::data";
    }

    public synchronized void add(Object connection, UUID uuid, Map<String, String> data) {
        String session = UUID.randomUUID().toString();
        Map<String, String> snapshot = new HashMap<>(data);
        snapshot.put("proxy-owner", owner);
        snapshot.put("session", session);
        // Retain the token even when Redis commits but its response is lost;
        // a later disconnect can still conditionally clean up that write.
        sessions.put(connection, session);
        jedis.hset(key(uuid), snapshot);
    }

    public synchronized boolean remove(Object connection, UUID uuid) {
        String session = sessions.remove(connection);
        if (session == null) return false;
        Removal removal = new Removal(uuid, session, System.currentTimeMillis());
        pending.put(session, removal);
        boolean owned = removeFromRedis(removal);
        pending.remove(session);
        return owned;
    }

    private boolean removeFromRedis(Removal removal) {
        return Long.valueOf(1).equals(jedis.eval(REMOVE, List.of(key(removal.uuid())),
                List.of(owner, removal.session(), Long.toString(removal.disconnectedAt()))));
    }

    public synchronized List<Cleanup> retryRemovals() {
        List<Cleanup> results = new ArrayList<>();
        var iterator = pending.values().iterator();
        while (iterator.hasNext() && results.size() < 64) {
            Removal removal = iterator.next();
            try {
                boolean owned = removeFromRedis(removal);
                iterator.remove();
                results.add(new Cleanup(removal.uuid(), owned));
            } catch (RuntimeException unavailable) {
                // Keep the token until a definite Redis response. A successful
                // delete with a lost response is safe to retry (CAS returns 0).
                break;
            }
        }
        return results;
    }

    // Player implementations may define equality by UUID; routing epochs must
    // instead follow the actual connection object, including local reconnects.
    public static final class ConnectionKey {
        private final Object player;

        public ConnectionKey(Object player) { this.player = player; }

        @Override
        public boolean equals(Object other) {
            return other instanceof ConnectionKey key && player == key.player;
        }

        @Override
        public int hashCode() { return System.identityHashCode(player); }
    }
}
