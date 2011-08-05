/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process.internal.child

import spock.lang.Specification
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import org.gradle.cache.CacheRepository
import org.gradle.cache.CacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.cache.DirectoryCacheBuilder

class WorkerProcessClassPathProviderTest extends Specification {
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    private final CacheRepository cacheRepository = Mock()
    private final WorkerProcessClassPathProvider provider = new WorkerProcessClassPathProvider(cacheRepository)

    def returnsNullForUnknownClasspath() {
        expect:
        provider.findClassPath('unknown') == null
    }

    def createsTheWorkerClasspathOnDemand() {
        def cacheDir = tmpDir.dir
        def classesDir = cacheDir.file('classes')
        DirectoryCacheBuilder cacheBuilder = Mock()
        PersistentCache cache = Mock()
        def initializer = null

        when:
        def classpath = provider.findClassPath('WORKER_MAIN')

        then:
        1 * cacheRepository.cache('workerMain') >> cacheBuilder
        1 * cacheBuilder.withInitializer(!null) >> { args -> initializer = args[0]; return cacheBuilder }
        1 * cacheBuilder.open() >> { initializer.execute(cache); return cache }
        _ * cache.getBaseDir() >> cacheDir
        0 * cache._
        classpath == [classesDir] as Set
        classesDir.listFiles().length != 0
    }

    def reusesTheCachedClasspath() {
        def cacheDir = tmpDir.dir
        def classesDir = cacheDir.file('classes')
        DirectoryCacheBuilder cacheBuilder = Mock()
        PersistentCache cache = Mock()

        when:
        def classpath = provider.findClassPath('WORKER_MAIN')

        then:
        1 * cacheRepository.cache('workerMain') >> cacheBuilder
        1 * cacheBuilder.withInitializer(!null) >> cacheBuilder
        1 * cacheBuilder.open() >> cache
        _ * cache.getBaseDir() >> cacheDir
        0 * cache._
        classpath == [classesDir] as Set
    }
}
