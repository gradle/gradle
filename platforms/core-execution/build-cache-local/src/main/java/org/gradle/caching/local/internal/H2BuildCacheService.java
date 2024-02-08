/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.local.internal;

import com.google.common.annotations.VisibleForTesting;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.gradle.cache.HasCleanupAction;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.StatefulNextGenBuildCacheService;
import org.gradle.internal.time.Clock;
import org.h2.Driver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

/**
 * This was used in Build cache next gen prototype, but it's was not deleted, since most of logic will be reused for new local cache.
 *
 * TODO: Extract H2 specific code to a generic "H2Cache" class
 */
public class H2BuildCacheService implements StatefulNextGenBuildCacheService, HasCleanupAction {

    private static final String DATABASE_NAME = "filestore";

    private final int removeUnusedEntriesAfterDays;
    private HikariDataSource dataSource;
    private final Clock clock;
    private final Path dbPath;
    private final int maxPoolSize;

    public H2BuildCacheService(Path dbPath, int maxPoolSize, int removeUnusedEntriesAfterDays, Clock clock) {
        this.dbPath = dbPath;
        this.maxPoolSize = maxPoolSize;
        this.clock = clock;
        this.removeUnusedEntriesAfterDays = removeUnusedEntriesAfterDays;
    }

    @Override
    public void open() {
        HikariConfig hikariConfig = new HikariConfig();
        String h2JdbcUrl = getH2JdbcUrl(dbPath, "");
        hikariConfig.setJdbcUrl(h2JdbcUrl);
        hikariConfig.setDriverClassName(Driver.class.getName());
        hikariConfig.setUsername("sa");
        hikariConfig.setPassword("");
        hikariConfig.setCatalog("filestore");
        hikariConfig.setPoolName("filestore-pool");
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setConnectionInitSql("select 1;");
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    private static String getH2JdbcUrl(Path dbPath, String additionalConfiguration) {
        // RETENTION_TIME=0 prevents uncontrolled DB growth with old pages retention
        // AUTO_COMPACT_FILL_RATE=0 disables compacting, we will compact on cleanup
        // COMPRESS=false disables compression, we already do gzip compression
        // We use MODE=MySQL so we can use INSERT IGNORE
        String configuration = "RETENTION_TIME=0;AUTO_COMPACT_FILL_RATE=0;COMPRESS=false;MODE=MySQL" + additionalConfiguration;
        return String.format("jdbc:h2:file:%s;%s;INIT=runscript from 'classpath:/h2/schemas/org.gradle.caching.local.internal.H2BuildCacheService.sql'", dbPath.resolve(DATABASE_NAME), configuration);
    }

    @Override
    public boolean contains(BuildCacheKey key) {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("select entry_key from filestore.catalog where entry_key = ?")) {
                stmt.setString(1, key.getHashCode());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw new BuildCacheException("contains " + key, e);
        }
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "select entry_content from filestore.catalog where entry_key = ?;" +
                    "update filestore.lru set entry_accessed = ? where entry_key = ?;"
            )) {
                stmt.setString(1, key.getHashCode());
                stmt.setLong(2, clock.getCurrentTime());
                stmt.setString(3, key.getHashCode());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Blob content = rs.getBlob(1);
                        try (InputStream binaryStream = content.getBinaryStream()) {
                            reader.readFrom(binaryStream);
                        }
                        return true;
                    }
                    return false;
                }
            }
        } catch (SQLException | IOException e) {
            throw new BuildCacheException("loading " + key, e);
        }
    }

    @Override
    public void store(BuildCacheKey key, NextGenWriter writer) throws BuildCacheException {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "insert ignore into filestore.lru(entry_key, entry_accessed) values (?, ?);" +
                    "insert ignore into filestore.catalog(entry_key, entry_size, entry_content) values (?, ?, ?);"
            )) {
                try (InputStream input = writer.openStream()) {
                    stmt.setString(1, key.getHashCode());
                    stmt.setLong(2, clock.getCurrentTime());
                    stmt.setString(3, key.getHashCode());
                    stmt.setLong(4, writer.getSize());
                    stmt.setBinaryStream(5, input);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException | IOException e) {
            throw new BuildCacheException("storing " + key, e);
        }
    }

    @VisibleForTesting
    public boolean remove(BuildCacheKey key) throws BuildCacheException {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("delete from filestore.catalog where entry_key = ?")) {
                stmt.setString(1, key.getHashCode());
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new BuildCacheException("storing " + key, e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    /**
     * Cleanup is done after all Build cache controllers are closed, so we don't need to care about concurrent access.
     * Note: Cleanup will also shutdown the database.
     */
    @Override
    public void cleanup() {
        if (dataSource != null) {
            dataSource.close();
        }
        new H2Janitor(dbPath, removeUnusedEntriesAfterDays, clock).cleanup();
    }

    private static class H2Janitor implements HasCleanupAction {

        private final Clock clock;
        private final int removeUnusedEntriesAfterDays;
        private final Path dbPath;

        public H2Janitor(Path dbPath, int removeUnusedEntriesAfterDays, Clock clock) {
            this.dbPath = dbPath;
            this.removeUnusedEntriesAfterDays = removeUnusedEntriesAfterDays;
            this.clock = clock;
        }

        @Override
        public void cleanup() {
            deleteLruEntries();
        }

        private void deleteLruEntries() throws RuntimeException {
            try (Connection conn = getConnection()) {
                long deleteThresholdMillis = clock.getCurrentTime() - TimeUnit.DAYS.toMillis(removeUnusedEntriesAfterDays);
                try (PreparedStatement stmt = conn.prepareStatement(
                    "delete from filestore.catalog where entry_key in (select entry_key FROM filestore.lru where entry_accessed < ?);" +
                        "delete from filestore.lru where entry_accessed < ?;"
                )) {
                    stmt.setLong(1, deleteThresholdMillis);
                    stmt.setLong(2, deleteThresholdMillis);
                    stmt.execute();
                }
                try (Statement stat = conn.createStatement()) {
                    stat.execute("shutdown compact");
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete LRU entries", e);
            }
        }

        /**
         * We create a new connection for each cleanup via DriverManager, since we shutdown database manually
         */
        private Connection getConnection() throws SQLException {
            // Increase default max compact time to 10 seconds, so database can be compacted more efficiently
            String additionalConfiguration = ";MAX_COMPACT_TIME=10000";
            String h2JdbcUrl = getH2JdbcUrl(dbPath, additionalConfiguration);
            return DriverManager.getConnection(h2JdbcUrl, "sa", "");
        }
    }
}
