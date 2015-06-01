/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch

import org.gradle.api.Action
import org.gradle.api.internal.file.FileSystemSubset
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Requires(TestPrecondition.JDK7_OR_LATER)
class DefaultFileSystemChangeWaiterTest extends ConcurrentSpec {

    @Rule
    TestNameTestDirectoryProvider testDirectory

    def "can wait for filesystem change"() {
        when:
        def w = new DefaultFileSystemChangeWaiter(executorFactory, new DefaultFileWatcherFactory(executorFactory))
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()

        start {
            w.wait(f, c) {
                instant.notified
            }
            instant.done
        }

        then:
        waitFor.notified

        when:
        testDirectory.file("new") << "change"

        then:
        waitFor.done
    }

    def "escapes on cancel"() {
        when:
        def w = new DefaultFileSystemChangeWaiter(executorFactory, new DefaultFileWatcherFactory(executorFactory))
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()

        start {
            w.wait(f, c) {
                instant.notified
            }
            instant.done
        }

        then:
        waitFor.notified

        when:
        c.cancel()

        then:
        waitFor.done
    }

    def "escapes on exception"() {
        given:
        def onErrorReference = new AtomicReference<Action>()
        def fileWatcherFactory = Mock(FileWatcherFactory) {
            watch(_, _, _) >> { FileSystemSubset systemSubset, Action onError, FileWatcherListener listener ->
                onErrorReference.set(onError)
                Mock(FileWatcher)
            }

        }
        when:
        def w = new DefaultFileSystemChangeWaiter(executorFactory, fileWatcherFactory)
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()

        start {
            try {
                w.wait(f, c) {
                    instant.notified
                }
            } catch (Exception e) {
                instant.done
            }
        }

        then:
        waitFor.notified

        when:
        onErrorReference.get().execute(new Exception("Exception in file watching"))

        then:
        waitFor.done
    }

    @Unroll
    def "waits until there is a quiet period - #description"(String description, Closure fileChanger) {
        when:
        def quietPeriod = 1000L
        def w = new DefaultFileSystemChangeWaiter(executorFactory, new DefaultFileWatcherFactory(executorFactory), quietPeriod)
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()
        def testfile = testDirectory.file("testfile")

        start {
            w.wait(f, c) {
                instant.notified
            }
            instant.done
        }

        then:
        waitFor.notified

        when:
        def lastChangeRef = new AtomicLong(0)
        fileChanger(instant, testfile, lastChangeRef)

        then:
        waitFor.done
        lastChangeRef.get() != 0
        System.currentTimeMillis() - lastChangeRef.get() >= quietPeriod

        where:
        description            | fileChanger
        'append and close'     | this.&changeByAppendingAndClosing
        'append and keep open' | this.&changeByAppendingAndKeepingFileOpen
    }

    private void changeByAppendingAndClosing(instant, testfile, lastChangeRef) {
        for (int i = 0; i < 10; i++) {
            instant.assertNotReached('done')
            testfile << "change"
            lastChangeRef.set(System.currentTimeMillis())
            sleep(50)
        }
    }

    private void changeByAppendingAndKeepingFileOpen(instant, testfile, lastChangeRef) {
        testfile.withPrintWriter { PrintWriter out ->
            for (int i = 0; i < 10; i++) {
                instant.assertNotReached('done')
                out.println("change")
                out.flush()
                lastChangeRef.set(System.currentTimeMillis())
                sleep(50)
            }
        }
    }
}
