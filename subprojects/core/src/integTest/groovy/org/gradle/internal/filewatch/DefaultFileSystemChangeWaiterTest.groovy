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
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import java.util.concurrent.atomic.AtomicReference

class DefaultFileSystemChangeWaiterTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider testDirectory
    FileWatcherEventListener reporter = new LoggingFileWatchEventListener()
    def fileSystem = Stub(FileSystem)
    def pendingChangesListener = Mock(PendingChangesListener)

    def "can wait for filesystem change"() {
        when:
        def wf = new DefaultFileSystemChangeWaiterFactory(new DefaultFileWatcherFactory(executorFactory, fileSystem))
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()
        def w = wf.createChangeWaiter(pendingChangesListener, c, buildGateToken)

        start {
            w.watch(f)
            w.wait({
                instant.notified
            }, reporter)
            instant.done
        }

        then:
        waitFor.notified

        when:
        testDirectory.file("new") << "change"

        then:
        waitFor.done
        pendingChangesListener.onPendingChanges()

        cleanup:
        w.stop()
    }

    def "escapes on cancel"() {
        when:
        def wf = new DefaultFileSystemChangeWaiterFactory(new DefaultFileWatcherFactory(executorFactory, fileSystem))
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()
        def w = wf.createChangeWaiter(pendingChangesListener, c, buildGateToken)

        start {
            w.watch(f)
            w.wait({
                instant.notified
            }, reporter)
            instant.done
        }

        then:
        waitFor.notified

        when:
        c.cancel()

        then:
        waitFor.done

        cleanup:
        w.stop()
    }

    def "escapes on exception"() {
        given:
        def onErrorReference = new AtomicReference<Action>()
        def fileWatcherFactory = Mock(FileWatcherFactory) {
            watch(_, _) >> { Action onError, FileWatcherListener listener ->
                onErrorReference.set(onError)
                Mock(FileWatcher)
            }

        }
        when:
        def wf = new DefaultFileSystemChangeWaiterFactory(fileWatcherFactory)
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()
        def w = wf.createChangeWaiter(pendingChangesListener, c, buildGateToken)

        start {
            try {
                w.watch(f)
                w.wait({
                    instant.notified
                }, reporter)
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

        cleanup:
        w.stop()
    }

    def "waits until there is a quiet period"() {
        when:
        def quietPeriod = 1000L
        def wf = new DefaultFileSystemChangeWaiterFactory(new DefaultFileWatcherFactory(executorFactory, fileSystem), quietPeriod)
        def f = FileSystemSubset.builder().add(testDirectory.testDirectory).build()
        def c = new DefaultBuildCancellationToken()
        def w = wf.createChangeWaiter(pendingChangesListener, c, buildGateToken)
        def testfile = testDirectory.file("testfile")

        start {
            w.watch(f)
            w.wait({
                instant.notified
            }, reporter)
            instant.done
        }

        then:
        waitFor.notified

        when:
        writeToFileMultipleTimes(instant, testfile)

        and:
        def timestampForLastChange = System.nanoTime()
        logger.log("changing final")
        testfile << "final change"
        logger.log("changed")

        then:
        waitFor.done
        Math.round((System.nanoTime() - timestampForLastChange) / 1000000L) >= quietPeriod

        cleanup:
        w.stop()
    }

    private void writeToFileMultipleTimes(instant, testfile) {
        for (int i = 0; i < 10; i++) {
            instant.assertNotReached('done')

            logger.log("changing ${i + 1}")
            testfile << "change"
            logger.log("changed")
            sleep(50)
        }
    }

    class LoggingFileWatchEventListener implements FileWatcherEventListener {
        @Override
        void onChange(FileWatcherEvent event) {
            logger.log(event)
        }

        @Override
        void reportChanges(StyledTextOutput logger) {

        }
    }
}
