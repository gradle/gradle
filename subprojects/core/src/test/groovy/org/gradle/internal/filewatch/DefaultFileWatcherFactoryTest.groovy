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

import org.gradle.api.internal.file.FileSystemSubset
import org.gradle.internal.Pair
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Requires(TestPrecondition.JDK7_OR_LATER)
class DefaultFileWatcherFactoryTest extends AbstractFileWatcherTest {
    FileWatcher fileWatcher
    FileWatcherFactory fileWatcherFactory

    void setup() {
        fileWatcherFactory = new DefaultFileWatcherFactory(new DefaultExecutorFactory())
        fileSystemSubset = FileSystemSubset.builder().add(testDir.testDirectory).build()
    }

    void cleanup() {
        fileWatcher?.stop()
        if (fileWatcherFactory instanceof Stoppable) {
            fileWatcherFactory.stop()
        }
    }

    def "watch service should notify of new files"() {
        given:
        def listener = Mock(FileWatcherListener)
        def listenerCalledLatch = new CountDownLatch(1)
        when:
        fileWatcher = fileWatcherFactory.watch(onError, listener)
        fileWatcher.watch(fileSystemSubset)
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
        fileWatcher = fileWatcherFactory.watch(onError, listener)
        fileWatcher.watch(fileSystemSubset)
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
        fileWatcher = fileWatcherFactory.watch(onError, listener)
        fileWatcher.watch(fileSystemSubset)
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
        fileWatcher = fileWatcherFactory.watch(onError) { watcher, event ->
            def vals = [watcher.running]
            watcher.stop()
            block.set(vals << watcher.running)
        }
        fileWatcher.watch(fileSystemSubset)

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
        fileWatcher = fileWatcherFactory.watch(onError) { watcher, event ->
            eventLatch.countDown()
            try {
                waitOn(stopLatch)
            } catch(e) {}
            result.set(watcher.running)
        }
        fileWatcher.watch(fileSystemSubset)

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
        fileWatcher = fileWatcherFactory.watch(onError) { watcher, event ->
            watcherThread.set(Thread.currentThread())
        }
        fileWatcher.watch(fileSystemSubset)

        and:
        testDir.file("new") << "new"

        then:
        watcherThread.get().interrupt()
        await { assert !fileWatcher.running }
    }

    def "can interrupt watcher event delivery"() {
        when:
        def eventReceivedLatch = new CountDownLatch(1)
        def interruptedLatch = new CountDownLatch(1)
        fileWatcher = fileWatcherFactory.watch(onError) { watcher, event ->
            eventReceivedLatch.countDown()
            try {
                Thread.sleep(100000)
            } catch (InterruptedException e) {
                interruptedLatch.countDown()
                throw e
            }
        }
        fileWatcher.watch(fileSystemSubset)

        and:
        testDir.file("new") << "new"

        then:
        waitOn(eventReceivedLatch)
        fileWatcher.stop()
        waitOn(interruptedLatch)
    }

    def "watcher can detects all files added to watched directory"() {
        when:
        def eventReceivedLatch = new CountDownLatch(1)
        def filesAddedLatch = new CountDownLatch(1)
        def totalLatch = new CountDownLatch(10)

        fileWatcher = fileWatcherFactory.watch(onError) { watcher, event ->
            eventReceivedLatch.countDown()
            filesAddedLatch.await()
            if (event.type == FileWatcherEvent.Type.CREATE) {
                totalLatch.countDown()
            }
        }
        fileWatcher.watch(fileSystemSubset)

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
        fileWatcher = fileWatcherFactory.watch(onError) { watcher, event ->
            eventReceivedLatch.countDown()
            event.file.delete()
        }
        fileWatcher.watch(fileSystemSubset)

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
        fileWatcher = fileWatcherFactory.watch(
            { onErrorStatus.set(Pair.of(fileWatcher.running, it)) },
            { watcher, event -> throw new RuntimeException("!!") }
        )
        fileWatcher.watch(fileSystemSubset)

        and:
        testDir.file("new") << "new"

        then:
        await { assert !fileWatcher.running }
        !onErrorStatus.get().left
        onErrorStatus.get().right.message == "!!"
    }

    @Unroll
    def "watcher should register to watch all added directories - #scenario"() {
        given:
        def listener = Mock(FileWatcherListener)
        def subdir1 = testDir.file('src/main/java')
        def subdir1File = subdir1.file("SomeFile.java")
        def subdir2 = testDir.file('src/main/groovy')
        def subdir2File = subdir2.file("SomeFile.groovy")
        def filesToSee = ([subdir1File, subdir2File].collect { it.absolutePath } as Set).asSynchronized()
        def fileEventMatchedLatch = new CountDownLatch(filesToSee.size())

        if (subdir1Create) {
            subdir1.mkdirs()
        }
        if (subdir2Create) {
            subdir2.mkdirs()
        }
        if (parentCreate) {
            subdir1.getParentFile().mkdirs()
        }
        when:
        fileWatcher = fileWatcherFactory.watch(onError, listener)
        fileWatcher.watch(FileSystemSubset.builder().add(subdir1).build())
        subdir1.createFile("SomeFile.java").text = "Hello world"
        fileWatcher.watch(FileSystemSubset.builder().add(subdir2).build())
        subdir2.createFile("SomeFile.groovy").text = "Hello world"
        waitOn(fileEventMatchedLatch, false)
        then:
        (1.._) * listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            if (event.file.isFile()) {
                if (filesToSee.remove(event.file.absolutePath)) {
                    fileEventMatchedLatch.countDown()
                }
            }
        }
        filesToSee.isEmpty()
        where:
        scenario                         | subdir1Create | subdir2Create | parentCreate
        'both exist'                     | true          | true          | false
        'first exists'                   | true          | false         | false
        //'second exists'                  | false         | true          | false
        //'neither exists - parent exists' | false         | false         | true
        //'neither exists'                 | false         | false         | false
    }

    def "checks for scenario when the first directory to watch doesn't exist"() {
        // corner case scenario where the directory to watch doesn't exist
        // internally it's parent will be watched for changes
        //
        // src/main/java is the missing directory here, src/main/groovy exists.
        given:
        def listener = Mock(FileWatcherListener)
        def fileEventMatchedLatchReference = new AtomicReference(new CountDownLatch(1))
        def subdir1 = testDir.file('src/main/java')
        def subdir1File = subdir1.file("SomeFile.java")
        def subdir2 = testDir.file('src/main/groovy')
        def subdir2File = subdir2.file("SomeFile.groovy")
        def subdir2File2 = subdir2.file("SomeFile2.groovy")
        def filesSeen = ([] as Set).asSynchronized()
        listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            if (event.file.isFile()) {
                filesSeen.add(event.file.absolutePath)
                fileEventMatchedLatchReference.get().countDown()
            }
        }
        subdir2.mkdirs()

        // check that filtering works in this case and change events for
        // non-watched siblings don't get delivered
        when: 'Adds watch for non-existing directory'
        fileWatcher = fileWatcherFactory.watch(onError, listener)
        fileWatcher.watch(FileSystemSubset.builder().add(subdir1).build())

        and: 'Content in non-watched sibling directory changes'
        subdir2File.text = 'Some content'
        fileEventMatchedLatchReference.get().await(2000L, TimeUnit.MILLISECONDS)

        then: 'No changes should have been seen'
        filesSeen.isEmpty()

        // check that adding watch for sibling (src/main/groovy) later on is supported
        when: 'Adds watch for sibling directory and creates file'
        fileWatcher.watch(FileSystemSubset.builder().add(subdir2).build())
        subdir2File2.text = 'Some content'
        waitOn(fileEventMatchedLatchReference.get())

        then: 'New file should be seen'
        filesSeen == [subdir2File2.absolutePath] as Set

        // check that watching changes to src/main/java works after it gets created
        when: 'New file is created in first originally non-existing directory'
        filesSeen.clear()
        fileEventMatchedLatchReference.set(new CountDownLatch(1))
        subdir1.createDir()
        subdir1File.text = 'Some content'
        waitOn(fileEventMatchedLatchReference.get())

        then: 'New file should be seen'
        filesSeen == [subdir1File.absolutePath] as Set
    }


    def "should support watching directory that didn't exist when watching started"() {
        given:
        def listener = Mock(FileWatcherListener)
        def fileEventMatchedLatch = new CountDownLatch(1)
        def filesSeen = ([] as Set).asSynchronized()
        listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            if (event.file.isFile()) {
                filesSeen.add(event.file.absolutePath)
                fileEventMatchedLatch.countDown()
            }
        }
        fileWatcher = fileWatcherFactory.watch(onError, listener)
        def subdir = testDir.file('src/main/java')
        def subdirFile = subdir.file("SomeFile.java")

        when: 'Adds watch for non-existing directory'
        fileWatcher.watch(FileSystemSubset.builder().add(subdir).build())

        and: 'Creates file'
        subdirFile.createFile().text = 'Some content'
        waitOn(fileEventMatchedLatch)

        then: 'File should have been noticed'
        filesSeen.size() == 1
        filesSeen[0] == subdirFile.absolutePath
    }
}
