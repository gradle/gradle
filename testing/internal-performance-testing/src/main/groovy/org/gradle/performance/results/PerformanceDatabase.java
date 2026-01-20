/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.results;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class PerformanceDatabase {
    // This value specifies that a DB connection will stay at pool for at most 30 seconds
    private static final int MAX_LIFETIME_MS = 30 * 1000;
    private static final String PERFORMANCE_DB_URL_PROPERTY_NAME = "org.gradle.performance.db.url";
    private static final DateTimeFormatter PROFILING_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final String databaseName;
    private final List<ConnectionAction<Void>> databaseInitializers;
    private HikariDataSource dataSource;

    private static void logProfilingWithCallStack(String jsonPayload) {
        // Capture stack at call site. We'll still include this method in the trace, but skip it when printing.
        StackTraceElement[] stackTrace = new Exception("Call stack for profiling log").getStackTrace();
        String ts = LocalDateTime.now().format(PROFILING_TIMESTAMP_FORMATTER);
        System.out.println("[Profiling] " + ts + " " + jsonPayload);
        for (int i = 1; i < stackTrace.length; i++) {
            System.out.println("[Profiling] \tat " + stackTrace[i]);
        }
    }

    @SafeVarargs
    public PerformanceDatabase(String databaseName, ConnectionAction<Void>... schemaInitializers) {
        this.databaseName = databaseName;
        this.databaseInitializers = Arrays.asList(schemaInitializers);
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            long startTime = System.currentTimeMillis();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(getUrl());
            config.setUsername(getUserName());
            config.setPassword(getPassword());
            config.setMaximumPoolSize(1);
            config.setMaxLifetime(MAX_LIFETIME_MS);
            dataSource = new HikariDataSource(config);

            executeInitializers(dataSource);
            logProfilingWithCallStack(SQLProfilingData.create(
                "openDataSource",
                getUrl(),
                List.of("databaseName=" + databaseName),
                "poolSize=1,maxLifetimeMs=" + MAX_LIFETIME_MS,
                startTime
            ).toJson());
        }
        return dataSource.getConnection();
    }

    private void executeInitializers(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            for (ConnectionAction<Void> initializer : databaseInitializers) {
                try {
                    initializer.execute(connection);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 90096) {
                        System.out.println("Not enough permissions to migrate the performance database. This is okay if you are only trying to read.");
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    public static boolean isAvailable() {
        return System.getProperty(PERFORMANCE_DB_URL_PROPERTY_NAME) != null;
    }

    public void close() {
        if (dataSource != null) {
            long startTime = System.currentTimeMillis();
            try {
                dataSource.close();
            } finally {
                dataSource = null;
                logProfilingWithCallStack(SQLProfilingData.create(
                    "closeDataSource",
                    getUrl(),
                    List.of("databaseName=" + databaseName),
                    true,
                    startTime
                ).toJson());
            }
        }
    }

    public <T> T withConnection(ConnectionAction<T> action) throws SQLException {
        long openStartTime = System.currentTimeMillis();
        Connection connection = getConnection();
        int connectionId = System.identityHashCode(connection);
        logProfilingWithCallStack(SQLProfilingData.create(
            "openConnection",
            getUrl(),
            List.of("databaseName=" + databaseName, "connectionId=" + connectionId),
            true,
            openStartTime
        ).toJson());

        try {
            return action.execute(connection);
        } finally {
            long closeStartTime = System.currentTimeMillis();
            boolean closed = false;
            try {
                connection.close();
                closed = true;
            } finally {
                logProfilingWithCallStack(SQLProfilingData.create(
                    "closeConnection",
                    getUrl(),
                    List.of("databaseName=" + databaseName, "connectionId=" + connectionId),
                    closed,
                    closeStartTime
                ).toJson());
            }
        }
    }

    public <T> T withConnection(String actionName, ConnectionAction<T> action) {
        try {
            return withConnection(action);
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Could not %s from datastore '%s'.", actionName, getUrl()), e);
        }
    }

    public String getUrl() {
        String baseUrl = System.getProperty(PERFORMANCE_DB_URL_PROPERTY_NAME);
        if (baseUrl == null) {
            throw new RuntimeException("You need to specify a URL for the performance database");
        }
        StringBuilder url = new StringBuilder(baseUrl);
        if (!baseUrl.endsWith("/")) {
            url.append('/');
        }
        url.append(databaseName);
        return url.toString();
    }

    public String getUserName() {
        return System.getProperty("org.gradle.performance.db.username", "sa");
    }

    public String getPassword() {
        return System.getProperty("org.gradle.performance.db.password", "");
    }
}
