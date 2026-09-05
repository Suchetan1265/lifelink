package com.lifelink.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One embedded PostgreSQL for the whole test JVM. Real server, no Docker, so
 * Flyway migrations and native SQL behave exactly as they do in production.
 */
public final class EmbeddedPostgresServer {

    public static final String USERNAME = "postgres";
    public static final String PASSWORD = "postgres";

    private static EmbeddedPostgres server;

    private EmbeddedPostgresServer() {
    }

    public static synchronized int port() {
        if (server == null) {
            try {
                server = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start the embedded PostgreSQL server", e);
            }
        }
        return server.getPort();
    }

    public static String jdbcUrl(String database) {
        return "jdbc:postgresql://localhost:" + port() + "/" + database;
    }

    /**
     * Drops and recreates a database, for tests that cannot share the default one
     * because they assert on absolute row counts.
     *
     * @return the JDBC URL of the fresh database
     */
    public static String freshDatabase(String name) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("postgres"), USERNAME, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + name);
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the " + name + " test database", e);
        }
        return jdbcUrl(name);
    }
}
