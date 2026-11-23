package com.imaginarycode.minecraft.redisbungee.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import redis.clients.jedis.JedisPooled;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "METADATA_TEST_REDIS_PORT", matches = "[0-9]+")
class ProxyMetadataPublisherTest {
    private final String network = "test-" + UUID.randomUUID();

    private JedisPooled connect() {
        return new JedisPooled("127.0.0.1", Integer.parseInt(System.getenv("METADATA_TEST_REDIS_PORT")));
    }

    private String key(String proxy) {
        return "redisbungee::" + network + "::proxies::" + proxy + "::metadata";
    }

    @Test
    void replacementFencesOldRenewalAndCleanupAndRemovesParameters() {
        try (var redis = connect()) {
            var old = new ProxyMetadataPublisher(redis, network, "a", Map.of("voice-endpoint", "a:24454", "removed", "old"));
            old.publish();
            assertEquals("a:24454", redis.hget(key("a"), "param:voice-endpoint"));
            assertEquals("1", redis.hget(key("a"), "schema"));
            assertTrue(redis.ttl(key("a")) > 0 && redis.ttl(key("a")) <= 30);
            var next = new ProxyMetadataPublisher(redis, network, "a", Map.of("voice-endpoint", "[::1]:24454"));
            next.publish();
            String owner = redis.hget(key("a"), "owner");
            old.publish();
            old.close();
            assertEquals(owner, redis.hget(key("a"), "owner"));
            assertNull(redis.hget(key("a"), "param:removed"));
            assertEquals("[::1]:24454", redis.hget(key("a"), "param:voice-endpoint"));
            next.close();
            assertFalse(redis.exists(key("a")));
            next.publish();
            assertFalse(redis.exists(key("a")));
        }
    }

    @Test
    void scopesAreIndependentAndEmptyMetadataReplacesPriorConfiguration() {
        try (var redis = connect()) {
            var a = new ProxyMetadataPublisher(redis, network, "a", Map.of("voice-endpoint", "a:1"));
            var b = new ProxyMetadataPublisher(redis, network, "b", Map.of("voice-endpoint", "b:2"));
            var other = new ProxyMetadataPublisher(redis, network + "-other", "a", Map.of());
            a.publish(); b.publish(); other.publish();
            assertEquals("a:1", redis.hget(key("a"), "param:voice-endpoint"));
            assertEquals("b:2", redis.hget(key("b"), "param:voice-endpoint"));
            var empty = new ProxyMetadataPublisher(redis, network, "a", Map.of());
            empty.publish();
            assertNull(redis.hget(key("a"), "param:voice-endpoint"));
            assertEquals(2, redis.hlen(key("a")));
            a.close(); empty.close(); b.close(); other.close();
        }
    }

    @Test
    void expiresAfterCrashAndRecreatesAfterRedisDataLoss() throws Exception {
        try (var redis = connect()) {
            var publisher = new ProxyMetadataPublisher(redis, network, "a", Map.of("voice-endpoint", "a:24454"));
            publisher.publish();
            Thread.sleep((ProxyMetadataPublisher.TTL_SECONDS + 1L) * 1000);
            assertFalse(redis.exists(key("a")));
            publisher.publish();
            assertEquals("a:24454", redis.hget(key("a"), "param:voice-endpoint"));
            redis.del(key("a"));
            publisher.publish();
            assertTrue(redis.ttl(key("a")) > 0);
            publisher.close();
        }
    }

    @Test
    void ambiguousClaimFailureCannotStealFromNewLaunchOnReconnect() {
        try (var redis = connect(); var flaky = new JedisPooled("127.0.0.1",
                Integer.parseInt(System.getenv("METADATA_TEST_REDIS_PORT"))) {
            private boolean fail = true;
            @Override
            public Object eval(String script, java.util.List<String> keys, java.util.List<String> args) {
                Object result = super.eval(script, keys, args);
                if (fail) {
                    fail = false;
                    throw new redis.clients.jedis.exceptions.JedisConnectionException("Lost response");
                }
                return result;
            }
        }) {
            var old = new ProxyMetadataPublisher(flaky, network, "a", Map.of("voice-endpoint", "old:1"));
            assertThrows(RuntimeException.class, old::publish);
            var live = new ProxyMetadataPublisher(redis, network, "a", Map.of("voice-endpoint", "new:2"));
            live.publish();
            old.publish();
            old.close();
            assertEquals(live.owner(), redis.hget(key("a"), "owner"));
            assertEquals("new:2", redis.hget(key("a"), "param:voice-endpoint"));
            live.close();
        }
    }
}
