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

package org.gradle.cache.internal.h2;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.gradle.cache.internal.btree.PersistentMap;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.h2.Driver;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class H2PersistentMap<V> implements PersistentMap<String, V> {

    private final HikariDataSource dataSource;
    private Serializer<V> serializer;

    public H2PersistentMap(Serializer<V> serializer, Path dbPath, int maxPoolSize) {
        this.serializer = serializer;
        this.dataSource = createHikariDataSource(dbPath, maxPoolSize);
    }

    private static HikariDataSource createHikariDataSource(Path dbPath, int maxPoolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        // RETENTION_TIME=0 prevents uncontrolled DB growth with old pages retention
        // We use MODE=MySQL so we can use INSERT IGNORE
        String h2JdbcUrl = String.format("jdbc:h2:file:%s;RETENTION_TIME=0;MODE=MySQL;INIT=runscript from 'classpath:/h2/schemas/org.gradle.caching.local.internal.h2.H2PersistentMap" +
            ".sql'", dbPath.resolve("store"));
        hikariConfig.setJdbcUrl(h2JdbcUrl);
        hikariConfig.setDriverClassName(Driver.class.getName());
        hikariConfig.setUsername("sa");
        hikariConfig.setPassword("");
        hikariConfig.setCatalog("store");
        hikariConfig.setPoolName("store-pool");
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setConnectionInitSql("select 1;");
        return new HikariDataSource(hikariConfig);
    }

    @Override
    public V get(String key) {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("select entry_content from store.catalog where entry_key = ?")) {
                stmt.setString(1, key);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Blob content = rs.getBlob(1);
                        try (InputStream binaryStream = content.getBinaryStream()) {
                            return serializer.read(new KryoBackedDecoder(binaryStream));
                        }
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("get " + key, e);
        }
    }

    @Override
    public void put(String key, V value) {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("insert ignore into store.catalog(entry_key, entry_content) values (?, ?)")) {
                Blob blob = conn.createBlob();
                try (OutputStream os = blob.setBinaryStream(1)) {
                    KryoBackedEncoder encoder = new KryoBackedEncoder(os);
                    serializer.write(encoder, value);
                    encoder.flush();
                }
                stmt.setString(1, key);
                stmt.setBlob(2, blob);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("put " + key, e);
        }
    }

    @Override
    public void remove(String key) {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("delete from store.catalog where entry_key = ?")) {
                stmt.setString(1, key);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("remove " + key, e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
