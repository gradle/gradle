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
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.util.RedirectStdIn
import org.junit.Rule
import spock.lang.Specification

class DefaultFileSystemChangeWaiterTest extends Specification {
    @Rule
    RedirectStdIn redirectStdIn

    def "there should be only a single DisconnectableInputstream blocked reading on System.in"() {
        given:
        def executorFactory = new DefaultExecutorFactory()
        def fileWatcherFactory = Mock(FileWatcherFactory)
        def waiter = new DefaultFileSystemChangeWaiter(executorFactory, fileWatcherFactory)
        def taskFileSystemInputs = Mock(FileSystemSubset)
        def cancellationToken = new DefaultBuildCancellationToken()
        cancellationToken.cancel()
        def notifier = Mock(Runnable)
        when:
        for (int i = 0; i < 100; i++) {
            waiter.wait(taskFileSystemInputs, cancellationToken, notifier)
        }
        int disconnectableInputStreamThreadCount = Thread.allStackTraces.count { k, v ->
            k.name == 'DisconnectableInputStream source reader'
        }
        then:
        // there should be only 1 thread.
        // relax condition since there might be other threads from other tests in parallel test execution
        disconnectableInputStreamThreadCount < 10
    }
}
