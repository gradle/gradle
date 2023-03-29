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

package org.gradle.cache.internal

import org.gradle.cache.CacheBuilder
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.internal.serialize.NullSafeStringSerializer
import org.gradle.test.fixtures.concurrent.TestExecutor
import org.gradle.test.fixtures.concurrent.TestExecutorFactory
import org.gradle.test.fixtures.concurrent.TestLogger
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.FileLockManager.LockMode.OnDemand
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class IndexedCacheCleanupTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def cacheDir = tmpDir.file("dir")
    def executor = new TestExecutor(new TestLogger())
    def executorFactory = new TestExecutorFactory(executor)
    def metaDataProvider = new DefaultProcessMetaDataProvider(NativeServicesTestFixture.getInstance().get(ProcessEnvironment));
    def lockManager = new DefaultFileLockManager(metaDataProvider, new NoOpFileLockContentionHandler())

    def "can cleanup indexed cache"() {
        def store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", CacheBuilder.LockTarget.DefaultTarget, mode(OnDemand), null, lockManager, executorFactory, new NoOpProgressLoggerFactory())
        store.open()

        when:
        def params = IndexedCacheParameters.of("myCache", String, new NullSafeStringSerializer()).withCleanup()
        def cache = store.createIndexedCache(params)
        store.useCache {
            cache.put("key", "value")
        }
        store.cleanup()

        then:
        noExceptionThrown()

        cleanup:
        store.close()
    }
}
