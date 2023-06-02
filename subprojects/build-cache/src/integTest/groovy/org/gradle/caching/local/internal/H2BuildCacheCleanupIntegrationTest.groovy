/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.local.internal

import org.gradle.caching.internal.DefaultBuildCacheKey
import org.gradle.caching.internal.NextGenBuildCacheService
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.hash.HashCode
import org.gradle.internal.time.Time

import java.nio.file.Files

class H2BuildCacheCleanupIntegrationTest extends AbstractBuildCacheCleanupIntegrationTest {
    @Override
    String getBuildCacheName() {
        return "Build cache NG"
    }

    @Override
    void createBuildCacheEntry(String key, File value, long timestamp) {
        try (H2BuildCacheService cacheService = new H2BuildCacheService(cacheDir.toPath(), 10, Integer.MAX_VALUE, { timestamp })) {
            cacheService.open()
            cacheService.store(new DefaultBuildCacheKey(HashCode.fromString(key)), new NextGenBuildCacheService.NextGenWriter() {
                @Override
                InputStream openStream() throws IOException {
                    return new FileInputStream(value)
                }

                @Override
                void writeTo(OutputStream output) throws IOException {
                    Files.copy(value.toPath(), output)
                }

                @Override
                long getSize() {
                    return value.size()
                }
            })
        }
    }

    @Override
    boolean existsBuildCacheEntry(String key) {
        try (H2BuildCacheService cacheService = new H2BuildCacheService(cacheDir.toPath(), 10, Integer.MAX_VALUE, Time.clock())) {
            cacheService.open()
            def buildCacheKey = new DefaultBuildCacheKey(HashCode.fromString(key))
            cacheService.contains(buildCacheKey)
        }
    }

    @Override
    AbstractIntegrationSpec withEnabledBuildCache() {
        withBuildCacheNg()
    }
}
