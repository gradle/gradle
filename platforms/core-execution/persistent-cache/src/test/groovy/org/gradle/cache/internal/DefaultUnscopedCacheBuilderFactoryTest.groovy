/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.cache.PersistentCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.FileLockManager.LockMode.OnDemand
import static org.gradle.cache.FileLockManager.LockMode.Shared
import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode

class DefaultUnscopedCacheBuilderFactoryTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    private final TestFile homeDir = tmpDir.createDir("home")
    private final TestFile sharedCacheDir = homeDir.file("caches")
    private final Map<String, ?> properties = [a: "value", b: "value2"]
    private final CacheFactory cacheFactory = Mock()
    private final PersistentCache cache = Mock()
    private final DefaultUnscopedCacheBuilderFactory repository = new DefaultUnscopedCacheBuilderFactory(cacheFactory)

    void createsGlobalDirectoryBackedCache() {
        when:
        def result = repository.cache(sharedCacheDir).open()

        then:
        result == cache
        1 * cacheFactory.open(sharedCacheDir, null, [:], mode(Shared), null, null) >> cache
        0 * cacheFactory._
    }

    void createsGlobalCacheWithProperties() {
        when:
        repository.cache(sharedCacheDir).withProperties(properties).open()

        then:
        1 * cacheFactory.open(sharedCacheDir, null, properties, mode(Shared), null, null) >> cache
    }

    void canSpecifyInitializerActionForDirectoryCache() {
        Action<?> action = Mock()

        when:
        repository.cache(sharedCacheDir).withInitializer(action).open()

        then:
        1 * cacheFactory.open(sharedCacheDir, null, [:], mode(Shared), action, null) >> cache
    }

    void canSpecifyLockModeForDirectoryCache() {
        when:
        repository.cache(sharedCacheDir).withInitialLockMode(OnDemand).open()

        then:
        1 * cacheFactory.open(sharedCacheDir, null, [:], mode(OnDemand), null, null) >> cache
    }

    void canSpecifyDisplayNameForDirectoryCache() {
        when:
        repository.cache(sharedCacheDir).withDisplayName("<cache>").open()

        then:
        1 * cacheFactory.open(sharedCacheDir, "<cache>", [:], mode(Shared), null, null) >> cache
    }
}
