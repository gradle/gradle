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

import org.gradle.internal.filewatch.FileWatchInputs
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class FileWatcherTaskTest extends Specification {
    WatchStrategy watchStrategy
    FileWatcherChangesNotifier changesNotifier
    int maxWatchLoops

    def setup() {
        watchStrategy = Mock(WatchStrategy)
        changesNotifier = Mock(FileWatcherChangesNotifier)
    }

    private FileWatcherTask createFileWatcherTask() {
        new FileWatcherTask(watchStrategy, FileWatchInputs.newBuilder().build(), new AtomicBoolean(true), Mock(Runnable)) {
            int watchLoopCounter = 0

            @Override
            protected FileWatcherChangesNotifier createChangesNotifier(Runnable callback) {
                return changesNotifier
            }

            @Override
            protected boolean watchLoopRunning() {
                watchLoopCounter++ < maxWatchLoops
            }
        }
    }

    def "test FileWatcherTask interaction with WatchStrategy"() {
        given:
        maxWatchLoops = 3
        when:
        createFileWatcherTask().run()
        then:
        1 * changesNotifier.reset()
        then:
        3 * watchStrategy.pollChanges(_, _, _)
        3 * changesNotifier.handlePendingChanges()
        then:
        1 * watchStrategy.close()
        0 * _._
    }
}
