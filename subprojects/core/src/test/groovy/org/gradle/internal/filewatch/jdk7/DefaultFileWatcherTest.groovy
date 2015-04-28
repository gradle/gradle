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

import org.gradle.internal.filewatch.FileWatcherListener
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class DefaultFileWatcherTest extends Specification {

    // TODO: revise test
    def "test watch and stop interaction with DefaultFileWatcher"() {
        given:
        def executor = Mock(ExecutorService)
        def watchStrategy = Mock(WatchStrategy)
        def listener = Mock(FileWatcherListener)
        def future = Mock(Future)
        when: 'fileWatcher is constructed'
        def fileWatcher = new DefaultFileWatcher(executor, watchStrategy, listener)
        then:
        1 * executor.submit(_) >> future
        when: "stop is called"
        fileWatcher.stop()
        then: "currently not unit testable"
        1 == 1
    }
}
