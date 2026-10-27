package com.obsidian.support;

import com.github.fppt.jedismock.RedisServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One real PostgreSQL 16 (schema from db/init.sql) and one in-process Redis per
 * JVM, shared by every integration test and by the bootTestRun dev runtime.
 */
public final class TestInfra {

    private static EmbeddedPostgres postgres;
    private static RedisServer redis;

    private TestInfra() {}

    public static synchronized void start() {
        if (postgres != null) {
            return;
        }
        try {
            postgres = EmbeddedPostgres.builder().start();
            applySchema(Path.of("..", "db", "init.sql"));
            redis = RedisServer.newRedisServer().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(TestInfra::stop));
    }

    public static void applySchema(Path sqlFile) {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(sqlFile));
        } catch (SQLException e) {
            throw new IllegalStateException("Applying " + sqlFile + " failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String jdbcUrl() {
        return postgres.getJdbcUrl("postgres", "postgres");
    }

    public static String redisHost() {
        return redis.getHost();
    }

    public static int redisPort() {
        return redis.getBindPort();
    }

    private static void stop() {
        try {
            redis.stop();
            postgres.close();
        } catch (IOException ignored) {
            // JVM is exiting.
        }
    }
}
