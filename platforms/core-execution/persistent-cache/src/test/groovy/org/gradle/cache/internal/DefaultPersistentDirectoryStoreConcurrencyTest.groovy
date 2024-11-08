/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.serialize.NullSafeStringSerializer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.cache.FileLockManager.LockMode.OnDemand
import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode

class DefaultPersistentDirectoryStoreConcurrencyTest extends ConcurrentSpec {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def cacheDir = tmpDir.file("dir")
    def metaDataProvider = new DefaultProcessMetaDataProvider(NativeServicesTestFixture.getInstance().get(ProcessEnvironment));
    def lockManager = new DefaultFileLockManager(metaDataProvider, new NoOpFileLockContentionHandler())
    final ManagedExecutor managedExecutor = executorFactory.create("test")

    @Issue("GRADLE-3206")
    def "can create new caches and access them in parallel"() {
        def store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(OnDemand), null, lockManager, managedExecutor)
        store.open()

        when:
        async {
            200.times { index ->
                start {
                    store.createIndexedCache(index.toString(), String, new NullSafeStringSerializer())
                    store.useCache {}
                }
            }
        }

        then:
        noExceptionThrown()

        cleanup:
        store.close()
    }
}
