/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker.child

import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.cache.CacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class WorkerProcessClassPathProviderTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final GlobalScopedCacheBuilderFactory cacheBuilderFactory = Mock()
    final ModuleRegistry moduleRegistry = Mock()
    final WorkerProcessClassPathProvider provider = new WorkerProcessClassPathProvider(cacheBuilderFactory, moduleRegistry)

    def returnsNullForUnknownClasspath() {
        expect:
        provider.findClassPath('unknown') == null
    }

    def createsTheWorkerClasspathOnDemand() {
        def cacheDir = tmpDir.testDirectory
        def jarFile = cacheDir.file('gradle-worker.jar')
        CacheBuilder cacheBuilder = Mock()
        PersistentCache cache = Mock()
        def initializer = null

        when:
        def classpath = provider.findClassPath('WORKER_MAIN')

        then:
        1 * cacheBuilderFactory.createCacheBuilder('workerMain') >> cacheBuilder
        1 * cacheBuilder.withInitializer(!null) >> { args -> initializer = args[0]; return cacheBuilder }
        1 * cacheBuilder.withLockOptions(_) >> cacheBuilder
        1 * cacheBuilder.open() >> { initializer.execute(cache); return cache }
        _ * cache.getBaseDir() >> cacheDir
        1 * cache.close()
        0 * cache._
        classpath.asFiles == [jarFile]
        jarFile.file
    }

    def reusesTheCachedClasspath() {
        def cacheDir = tmpDir.testDirectory
        def jarFile = cacheDir.file('gradle-worker.jar')
        CacheBuilder cacheBuilder = Mock()
        PersistentCache cache = Mock()

        when:
        def classpath = provider.findClassPath('WORKER_MAIN')

        then:
        1 * cacheBuilderFactory.createCacheBuilder('workerMain') >> cacheBuilder
        1 * cacheBuilder.withLockOptions(_) >> cacheBuilder
        1 * cacheBuilder.withInitializer(!null) >> cacheBuilder
        1 * cacheBuilder.open() >> cache
        _ * cache.getBaseDir() >> cacheDir
        1 * cache.close()
        0 * cache._
        classpath.asFiles == [jarFile]
    }
}
