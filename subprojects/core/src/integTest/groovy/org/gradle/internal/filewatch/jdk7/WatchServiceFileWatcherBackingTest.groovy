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
import org.apache.log4j.Logger
import org.gradle.api.Action
import org.gradle.api.internal.file.FileSystemSubset
import org.gradle.internal.filewatch.AbstractFileWatcherTest
import org.gradle.internal.filewatch.FileWatcher
import org.gradle.internal.filewatch.FileWatcherEvent
import org.gradle.internal.filewatch.FileWatcherListener
import org.gradle.util.UsesNativeServices
import spock.lang.AutoCleanup
import spock.lang.Unroll

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.WatchService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

import static org.gradle.internal.filewatch.jdk7.WatchServiceFileWatcherBackingTest.DirNotExistsTestScenario.SIBLINGS_PARENT_EXISTS_INITIALLY
import static org.gradle.internal.filewatch.jdk7.WatchServiceFileWatcherBackingTest.DirNotExistsTestScenario.SIBLING_EXISTS_INITIALLY
import static org.gradle.internal.filewatch.jdk7.WatchServiceFileWatcherBackingTest.DirNotExistsTestScenario.SIBLING_NOT_EXISTING_INITIALLY

@UsesNativeServices
class WatchServiceFileWatcherBackingTest extends AbstractFileWatcherTest {
    @AutoCleanup("shutdown")
    def executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())

    Logger logger = Logger.getLogger(WatchServiceFileWatcherBackingTest)

    enum DirNotExistsTestScenario {
        SIBLING_EXISTS_INITIALLY,
        SIBLINGS_PARENT_EXISTS_INITIALLY,
        SIBLING_NOT_EXISTING_INITIALLY
    }

    @Unroll
    def "checks for scenario when the first directory to watch doesn't exist - #testScenario"() {
        // corner case scenario where the directory to watch doesn't exist
        // internally it's parent will be watched for changes
        //
        // src/main/java is the missing directory here
        given:
        def listener = Mock(FileWatcherListener)
        def fileEventMatchedLatchReference = new AtomicReference(new CountDownLatch(1))

        // this is the directory to watch, it's not existing when the listening starts
        def subjectDir = testDir.file('src/main/java')
        def subjectDirFile = subjectDir.file("SomeFile.java")

        // this is a sibling directory which shouldn't be watch
        def siblingDir = testDir.file('src/main/groovy')
        def siblingDirFile1 = siblingDir.file("SomeFile.groovy")
        def siblingDirFile2 = siblingDir.file("SomeFile2.groovy")

        // holds files seen by the file watcher
        def filesSeen = ([] as Set).asSynchronized()
        listener.onChange(_, _) >> { FileWatcher watcher, FileWatcherEvent event ->
            if (event.file.isFile()) {
                logger.debug "file seen ${event.file.absolutePath}"
                filesSeen.add(event.file.absolutePath)
                fileEventMatchedLatchReference.get().countDown()
            }
        }

        def watchService = FileSystems.getDefault().newWatchService()
        // holds all registered watches so that we can check that sibling wasn't watched at all
        def registeredWatches = [].asSynchronized()
        def watchServiceRegistrar = new WatchServiceRegistrar(watchService, listener) {
            @Override
            protected void watchDir(Path dir) throws IOException {
                registeredWatches.add(dir.toFile().absolutePath)
                super.watchDir(dir)
            }
        }

        def fileWatcher = new WatchServiceFileWatcherBacking(onError, listener, watchService, watchServiceRegistrar).start(executorService)

        if (testScenario == SIBLING_EXISTS_INITIALLY) {
            siblingDir.mkdirs()
        } else if (testScenario == SIBLINGS_PARENT_EXISTS_INITIALLY) {
            // just create the parent
            siblingDir.getParentFile().mkdirs()
        }

        // check that filtering works in this case and change events for
        // non-watched siblings don't get delivered
        when:
        logger.debug 'Adds watch for non-existing directory'
        fileWatcher.watch(FileSystemSubset.builder().add(subjectDir).build())

        and:
        logger.debug 'Content in non-watched sibling directory changes'
        if (!siblingDir.exists()) {
            siblingDir.mkdirs()
        }
        siblingDirFile1.text = 'Some content'
        waitOn(fileEventMatchedLatchReference.get(), false)

        then: 'No changes should have been seen'
        filesSeen.isEmpty()
        and: 'Should have not registered to watching sibling directory'
        !registeredWatches.contains(siblingDir.absolutePath)

        // check that adding watch for sibling (src/main/groovy) later on is supported
        when:
        logger.debug 'Adds watch for sibling directory and creates file'
        fileWatcher.watch(FileSystemSubset.builder().add(siblingDir).build())
        siblingDirFile2.text = 'Some content'
        waitOn(fileEventMatchedLatchReference.get())

        then: 'New file should be seen'
        filesSeen == [siblingDirFile2.absolutePath] as Set

        // check that watching changes to src/main/java works after it gets created
        when:
        logger.debug 'New file is created in first originally non-existing directory'
        filesSeen.clear()
        fileEventMatchedLatchReference.set(new CountDownLatch(1))
        subjectDir.createDir()
        subjectDirFile.text = 'Some content'
        waitOn(fileEventMatchedLatchReference.get())

        then: 'New file should be seen'
        filesSeen == [subjectDirFile.absolutePath] as Set

        cleanup:
        fileWatcher.stop()

        where:
        testScenario << [SIBLING_EXISTS_INITIALLY, SIBLINGS_PARENT_EXISTS_INITIALLY, SIBLING_NOT_EXISTING_INITIALLY]
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
