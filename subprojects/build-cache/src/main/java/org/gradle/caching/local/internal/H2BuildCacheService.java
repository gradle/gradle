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
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.NextGenBuildCacheService;
import org.h2.Driver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class H2BuildCacheService implements NextGenBuildCacheService {

    private final HikariDataSource dataSource;

    public H2BuildCacheService(Path dbPath, int maxPoolSize) {
        this.dataSource = createHikariDataSource(dbPath, maxPoolSize);
    }

    private static HikariDataSource createHikariDataSource(Path dbPath, int maxPoolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        // RETENTION_TIME=0 prevents uncontrolled DB growth with old pages retention
        // We use MODE=MySQL so we can use INSERT IGNORE
        String h2JdbcUrl = String.format("jdbc:h2:file:%s;RETENTION_TIME=0;MODE=MySQL;INIT=runscript from 'classpath:/h2/schemas/org.gradle.caching.local.internal.H2BuildCacheService.sql'", dbPath.resolve("filestore"));
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
        } catch (SQLException | IOException e) {
            throw new BuildCacheException("loading " + key, e);
        }
    }

    @Override
    public void store(BuildCacheKey key, NextGenWriter writer) throws BuildCacheException {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("insert ignore into filestore.catalog(entry_key, entry_size, entry_content) values (?, ?, ?)")) {
                try (InputStream input = writer.openStream()) {
                    stmt.setString(1, key.getHashCode());
                    stmt.setLong(2, writer.getSize());
                    stmt.setBinaryStream(3, input);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException | IOException e) {
            throw new BuildCacheException("storing " + key, e);
        }
    }

    @Override
    public void close() throws IOException {
        dataSource.close();
    }
}
