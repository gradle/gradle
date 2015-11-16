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
import org.gradle.internal.filewatch.FileWatcherListener
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.file.WatchService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class WatchServiceFileWatcherBackingTest extends Specification {
    @AutoCleanup("shutdown")
    def executorService = Executors.newCachedThreadPool()

    def "a stopped filewatcher shouldn't get started"() {
        given:
        def fileSystemSubset = FileSystemSubset.builder().build()
        def onError = Mock(Action)
        def listener = Mock(FileWatcherListener)
        def watchService = Mock(WatchService)
        def fileWatcher = new WatchServiceFileWatcherBacking(fileSystemSubset, onError, listener, watchService)
        def listenerExecutorService = MoreExecutors.listeningDecorator(executorService)
        def mockExecutorService = Mock(ListeningExecutorService)
        def submitLatch = new CountDownLatch(1)

        when:
        def fileWatch = fileWatcher.start(mockExecutorService)
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
