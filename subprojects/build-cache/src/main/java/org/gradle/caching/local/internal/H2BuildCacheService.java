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
import org.flywaydb.core.Flyway;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Semaphore;

public class H2BuildCacheService implements BuildCacheService {

    private final HikariDataSource dataSource;
    private final Semaphore semaphore;

    public H2BuildCacheService(Path dbPath, int maxPoolSize) {
        this.dataSource = createHikariDataSource(dbPath, maxPoolSize);
        this.semaphore = new Semaphore(maxPoolSize);
        Flyway flyway = Flyway.configure()
            .schemas("filestore")
            .dataSource(dataSource)
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .failOnMissingLocations(true)
            .load();
        flyway.migrate();
    }

    private static HikariDataSource createHikariDataSource(Path dbPath, int maxPoolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        // RETENTION_TIME=0 prevents uncontrolled DB growth with old pages retention
        String h2JdbcUrl = String.format("jdbc:h2:file:%s;RETENTION_TIME=0", dbPath.resolve("filestore"));
        hikariConfig.setJdbcUrl(h2JdbcUrl);
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
        try {
            semaphore.acquire();
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement("select entry_key from filestore.catalog where entry_key = ?")) {
                    stmt.setString(1, key.getHashCode());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next();
                    }
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new BuildCacheException("contains " + key, e);
        } finally {
            semaphore.release();
        }
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        try {
            semaphore.acquire();
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement("select entry_content from filestore.catalog where entry_key = ?")) {
                    stmt.setString(1, key.getHashCode());
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
            }
        } catch (SQLException | IOException | InterruptedException e) {
            throw new BuildCacheException("load " + key, e);
        } finally {
            semaphore.release();
        }
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        try {
            semaphore.acquire();
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement("insert into filestore.catalog(entry_key, entry_size, entry_content) values (?, ?, ?)")) {
                    try (InputStream input = writer.openStream()) {
                        stmt.setString(1, key.getHashCode());
                        stmt.setLong(2, writer.getSize());
                        stmt.setBinaryStream(3, input);
                        stmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException | IOException | InterruptedException e) {
            throw new BuildCacheException("store " + key, e);
        } finally {
            semaphore.release();
        }
    }

    @Override
    public void close() throws IOException {
        dataSource.close();
    }
}
