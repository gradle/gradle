/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.BuildListener
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.internal.event.ListenerManager
import spock.lang.Specification

class TreeVisitorCacheExpirationStrategyTest extends Specification {

    def "listeners get registered in constructor and removed when stopping"() {
        given:
        CachingTreeVisitor cachingTreeVisitor = Mock()
        ListenerManager listenerManager = Mock()
        def buildListener
        def taskExecutionListener
        def taskExecutionGraphListener

        when:
        def treeVisitorCacheExpirationStrategy = new TreeVisitorCacheExpirationStrategy(cachingTreeVisitor, listenerManager)

        then:
        1 * listenerManager.addListener({
            if (it instanceof BuildListener) {
                buildListener = it
                return true
            }
            false
        })
        1 * listenerManager.addListener({
            if (it instanceof TaskExecutionListener) {
                taskExecutionListener = it
                return true
            }
            false
        })
        1 * listenerManager.addListener({
            if (it instanceof TaskExecutionGraphListener) {
                taskExecutionGraphListener = it
                return true
            }
            false
        })
        0 * _._

        when:
        treeVisitorCacheExpirationStrategy.stop()

        then:
        1 * listenerManager.removeListener(buildListener)
        1 * listenerManager.removeListener(taskExecutionListener)
        1 * listenerManager.removeListener(taskExecutionGraphListener)
        0 * _._
    }

}
