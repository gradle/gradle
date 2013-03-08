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

package org.gradle.wrapper

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import java.nio.channels.FileChannel
import java.nio.channels.FileLock

class ExclusiveFileAccessManagerTest extends ConcurrentSpec {

    @Rule TestNameTestDirectoryProvider testDirectoryProvider

    TestFile file(Object... path) {
        testDirectoryProvider.file(path)
    }

    def "will timeout"() {
        given:
        def file = file("foo")
        def called = false
        def manager = manager(locker({ null }))

        when:
        manager.access(file) { called = true }

        then:
        def e = thrown(RuntimeException)
        e.message ==~ /Timeout of.+/

        and:
        !called
    }

    def "will protect access"() {
        def file = file("foo")
        def manager = defaultManager()

        when:
        start {
            manager.access(file) {
                instant.now("fileLocked")
                thread.blockUntil.timedOut
            }
            instant.now("firstDone")
        }

        Exception timeoutException = null
        start {
            thread.blockUntil.fileLocked
            try {
                manager.access(file) {

                }
            } catch (Exception e) {
                timeoutException = e
            }

            instant.now("timedOut")
        }

        thread.blockUntil.firstDone

        then:
        timeoutException.message ==~ /Timeout of.+/
    }

    private ExclusiveFileAccessManager defaultManager() {
        manager(new ExclusiveFileAccessManager.DefaultLocker())
    }

    def
    ExclusiveFileAccessManager.Locker locker(Closure closure) {
        new ExclusiveFileAccessManager.Locker() {
            FileLock tryLock(FileChannel channel) throws IOException {
                closure(channel)
            }
        }
    }

    ExclusiveFileAccessManager manager(ExclusiveFileAccessManager.Locker locker) {
        new ExclusiveFileAccessManager(1000, 100, locker)
    }

}
