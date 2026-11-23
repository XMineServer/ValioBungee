package com.imaginarycode.minecraft.redisbungee.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import redis.clients.jedis.JedisPooled;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "METADATA_TEST_REDIS_PORT", matches = "[0-9]+")
class PlayerSessionStoreTest {
    @Test
    void delayedDisconnectCannotEraseNewLaunchOrLocalReconnect() {
        String network = UUID.randomUUID().toString();
        UUID player = UUID.randomUUID();
        String key = "redis-bungee::" + network + "::player::" + player + "::data";
        try (var redis = new JedisPooled("127.0.0.1", Integer.parseInt(System.getenv("METADATA_TEST_REDIS_PORT")))) {
            var old = new PlayerSessionStore(redis, network, "old-launch");
            var current = new PlayerSessionStore(redis, network, "new-launch");
            Object oldConnection = new Object(), firstLogin = new Object(), newLogin = new Object();
            Map<String, String> data = Map.of("proxy", "a", "ip", "127.0.0.1", "last-online", "0");
            old.add(oldConnection, player, data);
            current.add(firstLogin, player, data);
            String firstSession = redis.hget(key, "session");
            assertFalse(old.remove(oldConnection, player));
            assertEquals("0", redis.hget(key, "last-online"));
            assertEquals("new-launch", redis.hget(key, "proxy-owner"));
            current.add(newLogin, player, data);
            String newSession = redis.hget(key, "session");
            assertNotEquals(firstSession, newSession);
            assertFalse(current.remove(firstLogin, player));
            assertEquals(newSession, redis.hget(key, "session"));
            assertEquals("0", redis.hget(key, "last-online"));
            assertTrue(current.remove(newLogin, player));
            assertNull(redis.hget(key, "session"));
            assertNull(redis.hget(key, "proxy-owner"));
            assertNull(redis.hget(key, "proxy"));
            assertNotEquals("0", redis.hget(key, "last-online"));
            redis.del(key);
        }
    }

    @Test
    void equalPlayersStillHaveDistinctConnectionKeys() {
        String first = new String("same UUID"), next = new String("same UUID");
        assertNotEquals(new PlayerSessionStore.ConnectionKey(first), new PlayerSessionStore.ConnectionKey(next));
        assertEquals(new PlayerSessionStore.ConnectionKey(first), new PlayerSessionStore.ConnectionKey(first));
    }
}
