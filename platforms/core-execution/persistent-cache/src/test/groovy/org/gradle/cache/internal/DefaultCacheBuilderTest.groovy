/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.cache.internal


import org.gradle.cache.PersistentCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Consumer

import static org.gradle.cache.CacheCleanupStrategy.NO_CLEANUP
import static org.gradle.cache.FileLockManager.LockMode.OnDemand
import static org.gradle.cache.FileLockManager.LockMode.Shared
import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode

class DefaultCacheBuilderTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    private final TestFile homeDir = tmpDir.createDir("home")
    private final TestFile sharedCacheDir = homeDir.file("caches")
    private final Map<String, ?> properties = [a: "value", b: "value2"]
    private final CacheFactory cacheFactory = Mock()
    private final PersistentCache cache = Mock()
    private final DefaultCacheBuilder builder = new DefaultCacheBuilder(cacheFactory, sharedCacheDir)

    void createsGlobalDirectoryBackedCache() {
        when:
        def result = builder.open()

        then:
        result == cache
        1 * cacheFactory.open(sharedCacheDir, null, [:], mode(Shared), _, NO_CLEANUP) >> cache
        0 * cacheFactory._
    }

    void createsGlobalCacheWithProperties() {
        when:
        builder.withProperties(properties).open()

        then:
        1 * cacheFactory.open(sharedCacheDir, null, properties, mode(Shared), _, NO_CLEANUP) >> cache
    }

    void canSpecifyInitializerActionForDirectoryCache() {
        Consumer<?> action = Mock()

        when:
        builder.withInitializer(action).open()

        then:
        1 * cacheFactory.open(sharedCacheDir, null, [:], mode(Shared), action, NO_CLEANUP) >> cache
    }

    void canSpecifyLockModeForDirectoryCache() {
        when:
        builder.withInitialLockMode(OnDemand).open()

        then:
        1 * cacheFactory.open(sharedCacheDir, null, [:], mode(OnDemand), _, NO_CLEANUP) >> cache
    }

    void canSpecifyDisplayNameForDirectoryCache() {
        when:
        builder.withDisplayName("<cache>").open()

        then:
        1 * cacheFactory.open(sharedCacheDir, "<cache>", [:], mode(Shared), _, NO_CLEANUP) >> cache
    }
}
