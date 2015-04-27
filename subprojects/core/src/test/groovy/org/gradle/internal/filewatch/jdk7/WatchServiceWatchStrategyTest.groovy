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

import spock.lang.Specification

import java.nio.file.*
import java.util.concurrent.TimeUnit

class WatchServiceWatchStrategyTest extends Specification {
    def "test interactions in polling changes"() {
        given:
        WatchService watchService = Mock(WatchService)
        WatchServiceWatchStrategy watchStrategy = new WatchServiceWatchStrategy(watchService)
        def watchHandler = Mock(WatchHandler)
        def watchKey = Mock(WatchKey)
        def path = Mock(Path)
        def filePath = Mock(Path)
        def watchEvent = Mock(WatchEvent)
        when:
        watchStrategy.pollChanges(250, TimeUnit.MILLISECONDS, watchHandler)
        then:
        watchService.poll(250, TimeUnit.MILLISECONDS) >> watchKey
        1 * watchHandler.onActivation()
        watchKey.watchable() >> path
        watchKey.pollEvents() >> [watchEvent]
        watchEvent.kind() >> StandardWatchEventKinds.ENTRY_MODIFY
        watchEvent.context() >> filePath
        path.resolve(filePath) >> filePath
        1 * watchHandler.onChange({ChangeDetails changeDetails ->
            changeDetails.changeType == ChangeDetails.ChangeType.MODIFY &&
                    changeDetails.watchedPath.is(path) &&
                    changeDetails.fullItemPath.is(filePath)
        })
        1 * watchKey.reset()
        0 * _._
    }

    def "path should get registered to watch service"() {
        given:
        WatchService watchService = Mock(WatchService)
        WatchServiceWatchStrategy watchStrategy = new WatchServiceWatchStrategy(watchService)
        def path = Mock(Path)
        when:
        watchStrategy.watchSingleDirectory(path)
        then:
        path.register(watchService, _, _) >> {
            Mock(WatchKey)
        }
    }

    def "close calls close on WatchService"() {
        given:
        WatchService watchService = Mock(WatchService)
        WatchServiceWatchStrategy watchStrategy = new WatchServiceWatchStrategy(watchService)
        when:
        watchStrategy.close()
        then:
        watchService.close()
    }
}
