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
import org.gradle.internal.Pair
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.spockframework.lang.ConditionBlock
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@Requires(TestPrecondition.JDK7_OR_LATER)
class DefaultFileWatcherFactoryTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    FileWatcherFactory fileWatcherFactory
    long waitForEventsMillis = 3500L

    @AutoCleanup("stop")
    FileWatcher fileWatcher

    Throwable thrownInWatchExecution
    Action<? super Throwable> onError = {
        thrownInWatchExecution = it
    }

    FileSystemSubset fileSystemSubset

    void setup() {
        NativeServicesTestFixture.initialize()
        fileWatcherFactory = new DefaultFileWatcherFactory(new DefaultExecutorFactory())
        fileSystemSubset = FileSystemSubset.builder().add(testDir.testDirectory).build()
    }

    void cleanup() {
        fileWatcher?.stop()
        fileWatcherFactory?.stop()
        if (thrownInWatchExecution) {
            throw new ExecutionException("Exception was catched in executing watch", thrownInWatchExecution)
        }
    }

    def "watch service should notify of new files"() {
        given:
        def listener = Mock(FileWatcherListener)
        def listenerCalledLatch = new CountDownLatch(1)
        when:
        fileWatcher = fileWatcherFactory.watch(fileSystemSubset, onError, listener)
        File createdFile = testDir.file("newfile.txt")
        createdFile.text = "Hello world"
        waitOn(listenerCalledLatch)
        then:
        (1.._) * listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            handleEvent(event, listenerCalledLatch)
        }
        0 * _._
    }

    def "watch service should notify of new files in subdirectories"() {
        given:
        def listener = Mock(FileWatcherListener)
        def listenerCalledLatch = new CountDownLatch(1)
        def listenerCalledLatch2 = new CountDownLatch(1)
        when:
        fileWatcher = fileWatcherFactory.watch(fileSystemSubset, onError, listener)
        def subdir = testDir.createDir("subdir")
        subdir.createFile("somefile").text = "Hello world"
        waitOn(listenerCalledLatch)
        then:
        (1.._) * listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            handleEvent(event, listenerCalledLatch)
        }
        0 * _._
        when:
        subdir.file('someotherfile').text = "Hello world"
        waitOn(listenerCalledLatch2)
        then:
        (1.._) * listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            handleEvent(event, listenerCalledLatch2)
        }
        0 * _._
    }

    def handleEvent(FileWatcherEvent event, CountDownLatch listenerCalledLatch) {
        //println "event: $event"
        listenerCalledLatch.countDown()
    }

    def "watch service should register to watch subdirs at startup"() {
        given:
        def listener = Mock(FileWatcherListener)
        def subdir = testDir.createDir("subdir")
        def listenerCalledLatch = new CountDownLatch(1)
        when:
        fileWatcher = fileWatcherFactory.watch(fileSystemSubset, onError, listener)
        subdir.createFile("somefile").text = "Hello world"
        waitOn(listenerCalledLatch)
        then:
        (1.._) * listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            handleEvent(event, listenerCalledLatch)
        }
    }

    def "listener can terminate watcher"() {
        given:
        def block = this.<List<Boolean>> blockingVar()

        when:
        fileWatcher = fileWatcherFactory.watch(fileSystemSubset, onError) { watcher, event ->
            def vals = [watcher.running]
            watcher.stop()
            block.set(vals << watcher.running)
        }

        and:
        testDir.file("new") << "new"

        then:
        block.get() == [true, false]
        !fileWatcher.running
    }

    def "observer can terminate watcher"() {
        given:
        def eventLatch = new CountDownLatch(1)
        def stopLatch = new CountDownLatch(1)
        def result = this.<Boolean> blockingVar()

        when:
        fileWatcher = fileWatcherFactory.watch(fileSystemSubset, onError) { watcher, event ->
            eventLatch.countDown()
            waitOn(stopLatch)
            result.set(watcher.running)
        }

        and:
        testDir.file("new") << "new"

        then:
        waitOn(eventLatch)
        fileWatcher.stop()
        stopLatch.countDown()
        !result.get()
    }

    def "can interrupt watcher"() {
        given:
        def watcherThread = this.<Thread> blockingVar()

        when:
        fileWatcher = fileWatcherFactory.watch(fileSystemSubset, onError) { watcher, event ->
            watcherThread.set(Thread.currentThread())
        }

        and:
        testDir.file("new") << "new"

        then:
        watcherThread.get().interrupt()
        await { assert !fileWatcher.running }
    }

    def "watcher can detects all files added to watched directory"() {
        when:
        def eventReceivedLatch = new CountDownLatch(1)
        def filesAddedLatch = new CountDownLatch(1)
        def totalLatch = new CountDownLatch(10)

        fileWatcher = fileWatcherFactory.watch(fileSystemSubset, onError) { watcher, event ->
            eventReceivedLatch.countDown()
            filesAddedLatch.await()
            totalLatch.countDown()
        }

        testDir.file("1").createDir()
        eventReceivedLatch.await()

        testDir.file("1/2/3/4/5/6/7/8/9/10").createDir()
        filesAddedLatch.countDown()

        then:
        totalLatch.await()
    }

    def "watcher doesn't add directories that have been deleted after change detection"() {
        when:
        def eventReceivedLatch = new CountDownLatch(1)
        fileWatcher = fileWatcherFactory.watch(fileSystemSubset, onError) { watcher, event ->
            eventReceivedLatch.countDown()
            event.file.delete()
        }

        testDir.file("testdir").createDir()
        eventReceivedLatch.await()
        sleep(500)

        then:
        noExceptionThrown()
        thrownInWatchExecution == null
    }


    def "watcher will stop if listener throws and error is forwarded"() {
        when:
        def onErrorStatus = this.<Pair<Boolean, Throwable>> blockingVar()
        fileWatcher = fileWatcherFactory.watch(fileSystemSubset,
            { onErrorStatus.set(Pair.of(fileWatcher.running, it)) },
            { watcher, event -> throw new RuntimeException("!!") }
        )

        and:
        testDir.file("new") << "new"

        then:
        await { assert !fileWatcher.running }
        !onErrorStatus.get().left
        onErrorStatus.get().right.message == "!!"
    }

    private void waitOn(CountDownLatch latch) {
        //println "waiting..."
        latch.await(waitForEventsMillis, TimeUnit.MILLISECONDS)
    }

    @ConditionBlock
    private void await(Closure<?> closure) {
        ConcurrentTestUtil.poll(waitForEventsMillis / 1000 as int, closure)
    }

    private <T> BlockingVariable<T> blockingVar() {
        new BlockingVariable<T>(waitForEventsMillis / 1000)
    }
}
