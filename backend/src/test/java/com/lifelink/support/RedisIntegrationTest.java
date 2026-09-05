package com.lifelink.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

/**
 * Adds a real Redis to {@link IntegrationTest} for the cases that must prove
 * the Redis path itself works, rather than the fallbacks.
 *
 * <p>Tests extending {@link IntegrationTest} directly run with Redis absent,
 * which is deliberate: it keeps the graceful-degradation paths covered.
 */
public abstract class RedisIntegrationTest extends IntegrationTest {

    private static RedisServer redisServer;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        int port = startRedis();
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> port);
    }

    private static synchronized int startRedis() {
        if (redisServer == null) {
            try {
                int port = freePort();
                redisServer = RedisServer.newRedisServer().port(port).build();
                redisServer.start();
                Runtime.getRuntime().addShutdownHook(new Thread(RedisIntegrationTest::stopRedis));
                return port;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start the embedded Redis server", e);
            }
        }
        return redisServer.ports().get(0);
    }

    private static void stopRedis() {
        try {
            redisServer.stop();
        } catch (IOException ignored) {
            // The JVM is going away anyway.
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
