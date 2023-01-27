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
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.util.Path;

import java.io.IOException;

public class H2LocalCacheService implements BuildCacheService {

    private final HikariDataSource hikariDataSource;

    public H2LocalCacheService(Path dbPath) {
        this.hikariDataSource = createHikariDataSource(dbPath);
    }

    private static HikariDataSource createHikariDataSource(Path dbPath) {
        HikariConfig hikariConfig = new HikariConfig();
        // RETENTION_TIME=0 prevents uncontrolled DB growth with old pages retention
        String h2JdbcUrl = String.format("jdbc:h2:file:%s;RETENTION_TIME=0", dbPath);
        hikariConfig.setJdbcUrl(h2JdbcUrl);
        hikariConfig.setUsername("sa");
        hikariConfig.setPassword("");
        hikariConfig.setCatalog("filestore");
        hikariConfig.setPoolName("filestore-pool");
        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setConnectionInitSql("select 1;");
        hikariConfig.setRegisterMbeans(true);
        return new HikariDataSource(hikariConfig);
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        return false;
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        
    }

    @Override
    public void close() throws IOException {
        hikariDataSource.close();
    }
}
