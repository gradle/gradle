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

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.FileLockManager.LockMode.None
import static org.gradle.cache.internal.FileLockManager.LockMode.Shared
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultPersistentDirectoryStoreTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    final FileLockManager lockManager = Mock()
    final FileLock lock = Mock()
    final cacheDir = tmpDir.file("dir")
    final cacheFile = cacheDir.file("some-content.bin")
    final store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(None), lockManager, Mock(ExecutorFactory))

    def "has useful toString() implementation"() {
        expect:
        store.toString() == "<display> ($cacheDir)"
    }

    def "open creates directory if it does not exist"() {
        given:
        cacheDir.assertDoesNotExist()

        when:
        store.open()

        then:
        cacheDir.assertIsDir()
    }

    def "open does nothing when directory already exists"() {
        given:
        cacheDir.createDir()

        when:
        store.open()

        then:
        notThrown(RuntimeException)
    }

    def "open locks cache directory with requested mode"() {
        final store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(Shared), lockManager, Mock(ExecutorFactory))

        when:
        store.open()

        then:
        1 * lockManager.lock(cacheDir, mode(Shared), "<display> ($cacheDir)") >> lock

        when:
        store.close()

        then:
        _ * lock.state
        1 * lock.close()
        0 * _._
    }

    def "open does not lock cache directory when None mode requested"() {
        final store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(None), lockManager, Mock(ExecutorFactory))

        when:
        store.open()

        then:
        0 * _._

        when:
        store.close()

        then:
        0 * _._
    }

}
