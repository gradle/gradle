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
import org.gradle.CacheUsage
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.cache.*
import org.gradle.messaging.serialize.DefaultSerializer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.FileLockManager.LockMode.*
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultCacheRepositoryTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    private final TestFile homeDir = tmpDir.createDir("home")
    private final TestFile buildRootDir = tmpDir.createDir("build")
    private final TestFile sharedCacheDir = homeDir.file("caches")
    private final String version = GradleVersion.current().version
    private final Map<String, ?> properties = [a: "value", b: "value2"]
    private final CacheFactory cacheFactory = Mock()
    private final PersistentCache cache = Mock()
    private final Gradle gradle = Mock()
    private final DefaultCacheRepository repository = new DefaultCacheRepository(homeDir, null, CacheUsage.ON, cacheFactory)

    public void setup() {
        Project project = Mock()
        _ * cache.baseDir >> tmpDir.testDirectory
        _ * gradle.rootProject >> project
        _ * project.projectDir >> buildRootDir
    }

    public void createsGlobalDirectoryBackedStore() {
        when:
        def result = repository.store("a/b/c").open()

        then:
        result == cache
        1 * cacheFactory.openStore(sharedCacheDir.file(version, "a/b/c"), null, mode(Shared), null) >> cache
        0 * cacheFactory._
    }

    public void createsGlobalDirectoryBackedCache() {
        when:
        def result = repository.cache("a/b/c").open()

        then:
        result == cache
        1 * cacheFactory.open(sharedCacheDir.file(version, "a/b/c"), null, CacheUsage.ON, null, [:], mode(Shared), null) >> cache
        0 * cacheFactory._
    }

    public void createsGlobalIndexedCache() {
        given:
        PersistentIndexedCache<String, Integer> indexedCache = Mock()

        when:
        def result = repository.indexedCache(String.class, Integer.class, "key").open()

        then:
        result == indexedCache
        1 * cacheFactory.openIndexedCache(sharedCacheDir.file(version, "key"), CacheUsage.ON, null, [:], mode(Exclusive), {it instanceof DefaultSerializer}) >> indexedCache
        0 * cacheFactory._
    }

    public void createsGlobalStateCache() {
        given:
        PersistentStateCache<String> stateCache = Mock()

        when:
        def result = repository.stateCache(String.class, "key").open()

        then:
        result == stateCache
        1 * cacheFactory.openStateCache(sharedCacheDir.file(version, "key"), CacheUsage.ON, null, [:], mode(Exclusive), {it instanceof DefaultSerializer}) >> stateCache
        0 * cacheFactory._
    }

    public void createsGlobalCacheWithProperties() {
        when:
        repository.cache("a/b/c").withProperties(properties).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a/b/c"), null, CacheUsage.ON, null, properties, mode(Shared), null) >> cache
    }

    public void createsCacheWithLayout() {
        def layout = Mock(CacheLayout)
        def cacheDir = tmpDir.createDir("cache")
        when:
        repository.cache("a/b/c").withProperties(properties).withLayout(layout).open()

        then:
        1 * layout.getCacheDir(sharedCacheDir, null, "a/b/c") >> cacheDir
        1 * layout.applyLayoutProperties(properties) >> [version: "foo"]
        1 * cacheFactory.open(cacheDir, null, CacheUsage.ON, null, [version: "foo"], mode(Shared), null) >> cache
    }

    public void canSpecifyInitializerActionForDirectoryCache() {
        Action<?> action = Mock()

        when:
        repository.cache("a").withInitializer(action).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a"), null, CacheUsage.ON, null, [:], mode(Shared), action) >> cache
    }

    public void canSpecifyLockModeForDirectoryCache() {
        when:
        repository.cache("a").withLockOptions(mode(None)).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a"), null, CacheUsage.ON, null, [:], mode(None), null) >> cache
    }

    public void canSpecifyDisplayNameForDirectoryCache() {
        when:
        repository.cache("a").withDisplayName("<cache>").open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a"), "<cache>", CacheUsage.ON, null, [:], mode(Shared), null) >> cache
    }

    public void canSpecifyCacheValidatorForDirectoryCache() {
        CacheValidator validator = Mock();
        when:
        repository.cache("a").withValidator(validator).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a"), null, CacheUsage.ON, validator, [:], mode(Shared), null) >> cache
    }
}
