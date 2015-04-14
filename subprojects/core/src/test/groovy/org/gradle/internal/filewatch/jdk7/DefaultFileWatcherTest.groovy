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

import org.gradle.api.file.DirectoryTree
import org.gradle.internal.filewatch.FileWatchInputs
import org.gradle.internal.filewatch.FileWatchListener
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class DefaultFileWatcherTest extends Specification {

    def "test watch and stop interaction with DefaultFileWatcher"() {
        given:
        def executor = Mock(ExecutorService)
        def fileWatcher = new DefaultFileWatcher(executor) {
            @Override
            protected CountDownLatch createLatch() {
                CountDownLatch latch = super.createLatch()
                latch.countDown()
                latch
            }
        }
        def inputs = Mock(FileWatchInputs)
        def listener = Mock(FileWatchListener)
        def future = Mock(Future)
        when: "when watch is called, the inputs are read"
        fileWatcher.watch(inputs, listener)
        then:
        1 * executor.submit(_) >> future
        1 * inputs.getDirectoryTrees() >> {
            new ArrayList<DirectoryTree>()
        }
        1 * inputs.getFiles() >> {
            new ArrayList<File>()
        }
        0 * listener._
        0 * _._
        when: "watch is called a second time, it throws an exception"
        fileWatcher.watch(inputs, listener)
        then:
        thrown IllegalStateException
        when: "stop is called"
        fileWatcher.stop()
        then: "execution result is waited"
        1 * future.get(10, TimeUnit.SECONDS)
        when: "watch is called after stopping, it will succeed"
        fileWatcher.watch(inputs, listener)
        then:
        1 * executor.submit(_)
        1 * inputs.getDirectoryTrees() >> {
            new ArrayList<DirectoryTree>()
        }
        1 * inputs.getFiles() >> {
            new ArrayList<File>()
        }
    }
}
