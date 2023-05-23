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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.io.FileUtils;
import org.gradle.cache.HasCleanupAction;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.NextGenBuildCacheService;
import org.gradle.caching.internal.StatefulNextGenBuildCacheService;
import org.gradle.internal.time.Clock;
import org.h2.Driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * TODO: Extract H2 specific code to a generic "H2Cache" class
 */
public class H2BuildCacheService implements NextGenBuildCacheService, StatefulNextGenBuildCacheService {

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
        this.dataSource = open(DATABASE_NAME);
    }

    private HikariDataSource open(String databaseName) {
        HikariConfig hikariConfig = new HikariConfig();
        // RETENTION_TIME=0 prevents uncontrolled DB growth with old pages retention
        // AUTO_COMPACT_FILL_RATE=0 disables compacting, we will compact on cleanup
        // COMPRESS=false disables compression, we already do gzip compression
        // We use MODE=MySQL so we can use INSERT IGNORE
        String h2JdbcUrl = String.format("jdbc:h2:file:%s;RETENTION_TIME=0;AUTO_COMPACT_FILL_RATE=0;COMPRESS=false;MODE=MySQL;INIT=runscript from 'classpath:/h2/schemas/org.gradle.caching.local.internal.H2BuildCacheService.sql'", dbPath.resolve(databaseName));
        hikariConfig.setJdbcUrl(h2JdbcUrl);
        hikariConfig.setDriverClassName(Driver.class.getName());
        hikariConfig.setUsername("sa");
        hikariConfig.setPassword("");
        hikariConfig.setCatalog("filestore");
        hikariConfig.setPoolName("filestore-pool");
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setConnectionInitSql("select 1;");
        return new HikariDataSource(hikariConfig);
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

    @Override
    public void close() {
        dataSource.close();
    }

    @Override
    public void cleanup() {
        new H2Janitor(dbPath, removeUnusedEntriesAfterDays, clock).cleanup();
    }

    private class H2Janitor implements HasCleanupAction {

        private static final String NEW_DATABASE_NAME = "filestore.new";
        private static final String TEMP_DATABASE_NAME = "filestore.temp";

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
            deleteLeftovers();
            copyDatabase();
            replaceDatabaseAndOpen();
        }

        /**
         * Delete leftovers in case previous cleanup failed for some reason.
         */
        private void deleteLeftovers() {
            FileUtils.deleteQuietly(dbPath.resolve(NEW_DATABASE_NAME + ".mv.db").toFile());
            FileUtils.deleteQuietly(dbPath.resolve(TEMP_DATABASE_NAME + ".mv.db").toFile());
        }

        /**
         * Copies entries that are not too old to a new database returns the path to the new file of new database.
         */
        private void copyDatabase() {
            open();
            try (Connection conn = dataSource.getConnection()) {
                // Drop linked tables if they exist, to avoid "table already exists" error
                // in case a previous cleanup failed for some reason during the copy
                try (PreparedStatement stmt = conn.prepareStatement("drop table if exists filestore.newCatalog, filestore.newLru;")) {
                    stmt.execute();
                }

                open(NEW_DATABASE_NAME).close();
                Path newDatabasePath = dbPath.resolve(NEW_DATABASE_NAME);
                long deleteThresholdMillis = clock.getCurrentTime() - TimeUnit.DAYS.toMillis(removeUnusedEntriesAfterDays);
                try (PreparedStatement stmt = conn.prepareStatement(
                    String.format(
                        // Create linked tables to the new database that are located in the new file
                        "create linked table filestore.newCatalog('%s', 'jdbc:h2:file:%s', 'sa', '', 'filestore.catalog');" +
                        "create linked table filestore.newLru('%s', 'jdbc:h2:file:%s', 'sa', '', 'filestore.lru');" +

                        // Copy from the old database to the new database
                        "insert into filestore.newCatalog(entry_key, entry_size, entry_content)" +
                        "   select entry_key, entry_size, entry_content FROM filestore.catalog" +
                        "   where entry_key in (select entry_key FROM filestore.lru where entry_accessed > %s);" +
                        "insert into filestore.newLru(entry_key, entry_accessed)" +
                        "   select entry_key, entry_accessed FROM filestore.lru" +
                        "   where entry_key in (select entry_key FROM filestore.newCatalog);",
                        Driver.class.getName(), newDatabasePath, Driver.class.getName(), newDatabasePath, deleteThresholdMillis
                    )
                )) {
                    stmt.execute();
                }
            } catch (SQLException e) {
                FileUtils.deleteQuietly(dbPath.resolve(NEW_DATABASE_NAME + ".mv.db").toFile());
                throw new RuntimeException("H2 cleanup failed", e);
            }
        }

        private void replaceDatabaseAndOpen() {
            Path newDatabasePath = dbPath.resolve(NEW_DATABASE_NAME + ".mv.db");
            Path databasePath = dbPath.resolve(DATABASE_NAME + ".mv.db");
            Path tempDatabasePath = dbPath.resolve(TEMP_DATABASE_NAME + ".mv.db");
            dataSource.close();
            try {
                Files.move(databasePath, tempDatabasePath);
                Files.move(newDatabasePath, databasePath);
                Files.delete(tempDatabasePath);
            } catch (IOException e) {
                if (Files.exists(tempDatabasePath)) {
                    moveQuietly(tempDatabasePath, databasePath, REPLACE_EXISTING);
                }
                FileUtils.deleteQuietly(newDatabasePath.toFile());
                FileUtils.deleteQuietly(tempDatabasePath.toFile());
                throw new UncheckedIOException(e);
            } finally {
                open();
            }
        }

        private void moveQuietly(Path source, Path target, CopyOption... copyOptions) {
            try {
                Files.move(source, target, copyOptions);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
