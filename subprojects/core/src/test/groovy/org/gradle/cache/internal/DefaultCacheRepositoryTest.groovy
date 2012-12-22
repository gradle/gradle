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
import org.gradle.cache.CacheBuilder.VersionStrategy
import org.gradle.messaging.serialize.DefaultSerializer
import org.gradle.cache.PersistentCache
import org.gradle.cache.PersistentIndexedCache
import org.gradle.cache.PersistentStateCache
import org.gradle.util.GradleVersion
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification
import org.gradle.cache.CacheValidator

class DefaultCacheRepositoryTest extends Specification {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder()
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
        _ * cache.baseDir >> tmpDir.dir
        _ * gradle.rootProject >> project
        _ * project.projectDir >> buildRootDir
    }

    public void createsGlobalDirectoryBackedStore() {
        when:
        def result = repository.store("a/b/c").open()

        then:
        result == cache
        1 * cacheFactory.openStore(sharedCacheDir.file(version, "a/b/c"), null, FileLockManager.LockMode.Shared, null) >> cache
        0 * cacheFactory._
    }

    public void createsGlobalDirectoryBackedCache() {
        when:
        def result = repository.cache("a/b/c").open()

        then:
        result == cache
        1 * cacheFactory.open(sharedCacheDir.file(version, "a/b/c"), null, CacheUsage.ON, null, [:], FileLockManager.LockMode.Shared, null) >> cache
        0 * cacheFactory._
    }

    public void createsGlobalIndexedCache() {
        given:
        PersistentIndexedCache<String, Integer> indexedCache = Mock()

        when:
        def result = repository.indexedCache(String.class, Integer.class, "key").open()

        then:
        result == indexedCache
        1 * cacheFactory.openIndexedCache(sharedCacheDir.file(version, "key"), CacheUsage.ON, null, [:], FileLockManager.LockMode.Exclusive, {it instanceof DefaultSerializer}) >> indexedCache
        0 * cacheFactory._
    }

    public void createsGlobalStateCache() {
        given:
        PersistentStateCache<String> stateCache = Mock()

        when:
        def result = repository.stateCache(String.class, "key").open()

        then:
        result == stateCache
        1 * cacheFactory.openStateCache(sharedCacheDir.file(version, "key"), CacheUsage.ON, null, [:], FileLockManager.LockMode.Exclusive, {it instanceof DefaultSerializer}) >> stateCache
        0 * cacheFactory._
    }

    public void createsGlobalCacheWithProperties() {
        when:
        repository.cache("a/b/c").withProperties(properties).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a/b/c"), null, CacheUsage.ON, null, properties, FileLockManager.LockMode.Shared, null) >> cache
    }

    public void createsCacheForAGradleInstance() {
        when:
        repository.cache("a/b/c").forObject(gradle).open()

        then:
        1 * cacheFactory.open(buildRootDir.file(".gradle", version, "a/b/c"), null, CacheUsage.ON, null, [:], FileLockManager.LockMode.Shared, null) >> cache
    }

    public void createsCacheForAFile() {
        final TestFile dir = tmpDir.createDir("otherDir");

        when:
        repository.cache("a/b/c").forObject(dir).open()

        then:
        1 * cacheFactory.open(dir.file(".gradle", version, "a/b/c"), null, CacheUsage.ON, null, [:], FileLockManager.LockMode.Shared, null) >> cache
    }

    public void createsCrossVersionCacheThatIsInvalidatedOnVersionChange() {
        when:
        repository.cache("a/b/c").withVersionStrategy(VersionStrategy.SharedCacheInvalidateOnVersionChange).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file("noVersion", "a/b/c"), null, CacheUsage.ON, null, ["gradle.version": version], FileLockManager.LockMode.Shared, null) >> cache
    }

    public void createsCrossVersionCacheForAGradleInstanceThatIsInvalidatedOnVersionChange() {
        when:
        repository.cache("a/b/c").withVersionStrategy(VersionStrategy.SharedCacheInvalidateOnVersionChange).forObject(gradle).open()

        then:
        1 * cacheFactory.open(buildRootDir.file(".gradle", "noVersion", "a/b/c"), null, CacheUsage.ON, null, ["gradle.version": version], FileLockManager.LockMode.Shared, null) >> cache
    }

    public void canSpecifyInitializerActionForDirectoryCache() {
        Action<?> action = Mock()

        when:
        repository.cache("a").withInitializer(action).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a"), null, CacheUsage.ON, null, [:], FileLockManager.LockMode.Shared, action) >> cache
    }

    public void canSpecifyLockModeForDirectoryCache() {
        when:
        repository.cache("a").withLockMode(FileLockManager.LockMode.None).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a"), null, CacheUsage.ON, null, [:], FileLockManager.LockMode.None, null) >> cache
    }

    public void canSpecifyDisplayNameForDirectoryCache() {
        when:
        repository.cache("a").withDisplayName("<cache>").open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a"), "<cache>", CacheUsage.ON, null, [:], FileLockManager.LockMode.Shared, null) >> cache
    }

    public void canSpecifyCacheValidatorForDirectoryCache() {
        CacheValidator validator = Mock();
        when:
        repository.cache("a").withValidator(validator).open()

        then:
        1 * cacheFactory.open(sharedCacheDir.file(version, "a"), null, CacheUsage.ON, validator, [:], FileLockManager.LockMode.Shared, null) >> cache
    }
}
