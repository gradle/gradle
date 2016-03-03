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

package org.gradle.internal.filewatch.jdk7

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.Action
import org.gradle.api.internal.file.FileSystemSubset
import org.gradle.internal.filewatch.AbstractFileWatcherTest
import org.gradle.internal.filewatch.FileWatcher
import org.gradle.internal.filewatch.FileWatcherEvent
import org.gradle.internal.filewatch.FileWatcherListener
import spock.lang.AutoCleanup

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.WatchService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class WatchServiceFileWatcherBackingTest extends AbstractFileWatcherTest {
    @AutoCleanup("shutdown")
    def executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())

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
        def registeredWatches = [].asSynchronized()
        listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            if (event.file.isFile()) {
                filesSeen.add(event.file.absolutePath)
                fileEventMatchedLatchReference.get().countDown()
            }
        }
        def watchService = FileSystems.getDefault().newWatchService()
        def watchServiceRegistrar = new WatchServiceRegistrar(watchService, listener) {
            @Override
            protected void watchDir(Path dir) throws IOException {
                registeredWatches.add(dir.toFile().absolutePath)
                super.watchDir(dir)
            }
        }
        def fileWatcher = new WatchServiceFileWatcherBacking(onError, listener, watchService, watchServiceRegistrar).start(executorService)

        // check that filtering works in this case and change events for
        // non-watched siblings don't get delivered
        when: 'Adds watch for non-existing directory'
        fileWatcher.watch(FileSystemSubset.builder().add(subdir1).build())
        subdir2.mkdirs()

        and: 'Content in non-watched sibling directory changes'
        subdir2File.text = 'Some content'
        fileEventMatchedLatchReference.get().await(2000L, TimeUnit.MILLISECONDS)

        then: 'No changes should have been seen'
        filesSeen.isEmpty()
        and: 'Should have not registered to watching sibling directory'
        !registeredWatches.contains(subdir2.absolutePath)

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

    def "a stopped filewatcher shouldn't get started"() {
        given:
        def fileSystemSubset = FileSystemSubset.builder().build()
        def onError = Mock(Action)
        def listener = Mock(FileWatcherListener)
        def watchService = Mock(WatchService)
        def fileWatcher = new WatchServiceFileWatcherBacking(onError, listener, watchService)
        def listenerExecutorService = MoreExecutors.listeningDecorator(executorService)
        def mockExecutorService = Mock(ListeningExecutorService)
        def submitLatch = new CountDownLatch(1)

        when:
        def fileWatch = fileWatcher.start(mockExecutorService)
        fileWatch.watch(fileSystemSubset)
        fileWatch.stop()
        submitLatch.countDown()
        sleep(1000)

        then:
        mockExecutorService.submit(_) >> { Runnable r ->
            listenerExecutorService.submit(new Runnable() {
                @Override
                void run() {
                    submitLatch.await()
                    r.run()
                }
            })
        }
        0 * watchService.take()
    }
}
