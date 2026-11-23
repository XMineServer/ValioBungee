package com.imaginarycode.minecraft.redisbungee.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import redis.clients.jedis.JedisPooled;

import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: launches and stops ONLY its own disposable Redis process. */
@EnabledIfEnvironmentVariable(named = "METADATA_TEST_REDIS_BINARY", matches = ".+")
class ProxyMetadataReconnectTest {
    private Process start(int port) throws Exception {
        return new ProcessBuilder(System.getenv("METADATA_TEST_REDIS_BINARY"),
                "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--save", "", "--appendonly", "no")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private void awaitReady(JedisPooled redis) throws Exception {
        for (int i = 0; i < 100; i++) {
            try {
                if ("PONG".equals(redis.ping())) return;
            } catch (RuntimeException ignored) {
            }
            Thread.sleep(50);
        }
        fail("Disposable Redis did not start");
    }

    @Test
    void republishesAfterRealRedisStopAndRestart() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Process process = start(port);
        try (var redis = new JedisPooled("127.0.0.1", port)) {
            awaitReady(redis);
            var publisher = new ProxyMetadataPublisher(redis, "reconnect-test", "a", Map.of("voice-endpoint", "a:24454"));
            publisher.publish();
            String key = "redisbungee::reconnect-test::proxies::a::metadata";
            String owner = redis.hget(key, "owner");
            process.destroy();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertThrows(RuntimeException.class, publisher::publish);
            process = start(port);
            awaitReady(redis);
            assertFalse(redis.exists(key));
            publisher.publish();
            assertEquals(owner, redis.hget(key, "owner"));
            assertEquals("a:24454", redis.hget(key, "param:voice-endpoint"));
            publisher.close();
        } finally {
            process.destroy();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        }
    }
}
