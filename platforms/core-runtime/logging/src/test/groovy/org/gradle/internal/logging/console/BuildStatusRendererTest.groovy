/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier

class BuildStatusRendererTest extends OutputSpecification {
    def listener = Mock(OutputEventListener)
    def console = new ConsoleStub()
    def consoleMetaData = Mock(ConsoleMetaData)
    long currentTimeMs
    def renderer = new BuildStatusRenderer(listener, console.statusBar, console, consoleMetaData)

    @Override
    UpdateNowEvent updateNow() {
        return new UpdateNowEvent(currentTimeMs)
    }

    def "forwards event list to listener"() {
        def event = event("message")

        when:
        renderer.onOutput(event)

        then:
        1 * listener.onOutput(event)
    }

    def "formats given message with an incrementing timer"() {
        def event1 = startRootBuildOperation(1)
        def event2 = event('2')

        when:
        renderer.onOutput(event1)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> INITIALIZING  [0ms]'

        when:
        currentTimeMs += 1000
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> INITIALIZING  [1s]'
    }

    def "hides timer once the build finished"() {
        given:
        def event1 = startRootBuildOperation(1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> INITIALIZING  [0ms]'

        when:
        renderer.onOutput(complete(1, 'WAITING'))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................] WAITING'
    }

    def "formats build"() {
        given:
        def event1 = startRootBuildOperation(1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> INITIALIZING  [0ms]'

        when:
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................] WAITING'
    }

    def "counts configured projects"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startConfigureRootBuild(2, 1, 4)
        def event3 = startConfigureProject(3, 2)
        def event4 = startConfigureProject(4, 2)
        def event5 = startConfigureProject(5, 2)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> CONFIGURING  0 running, 4 queued   [0ms]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[=========.....................]> CONFIGURING  1 running, 3 queued   [0ms]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[########......................]> CONFIGURING  0 running, 3 queued   [0ms]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(complete(4))
        renderer.onOutput(event5)
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#####################.........]> CONFIGURING  0 running, 1 queued   [0ms]'
    }

    def "counts projects of nested builds"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startConfigureRootBuild(2, 1, 4)
        def event3 = startConfigureBuild(3, 2, 6)
        def event4 = startConfigureProject(4, 3)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> CONFIGURING  0 running, 10 queued   [0ms]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[========......................]> CONFIGURING  1 running,  9 queued   [0ms]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#######.......................]> CONFIGURING  0 running,  9 queued   [0ms]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#######.......................]> CONFIGURING  0 running,  9 queued   [0ms]'
    }

    def "counts executed work items"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 4)
        def event3 = startExecuteTask(3, 2)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> EXECUTING  0 running, 4 queued   [0ms]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[=========.....................]> EXECUTING  1 running, 3 queued   [0ms]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[########......................]> EXECUTING  0 running, 3 queued   [0ms]'

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[########......................]> EXECUTING  0 running, 3 queued   [0ms]'
    }

    def "counts work items of nested builds"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 4)
        def event3 = startExecuteBuild(3, 2, 6)
        def event4 = startExecuteTask(4, 3)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> EXECUTING  0 running, 10 queued   [0ms]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[========......................]> EXECUTING  1 running,  9 queued   [0ms]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#######.......................]> EXECUTING  0 running,  9 queued   [0ms]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#######.......................]> EXECUTING  0 running,  9 queued   [0ms]'
    }

    def "configuration phase runs until task graph execution"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startConfigureRootBuild(2, 1, 1)
        def event3 = startConfigureProject(3, 2)
        def event4 = startOther(4, 1)
        def event5 = startExecuteRootBuild(5, 1, 1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> CONFIGURING  0 running, 1 queued   [0ms]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[##############################]> CONFIGURING  0 running, 0 queued   [0ms]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[##############################]> CONFIGURING  0 running, 0 queued   [0ms]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(event5)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############...............]> EXECUTING  0 running, 1 queued^  [0ms]'

        when:
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................] WAITING'
    }

    def "carries configuration progress into execution"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startConfigureRootBuild(2, 1, 4))
        (3..6).each { id ->
            renderer.onOutput(startConfigureProject(id, 2))
            renderer.onOutput(complete(id))
        }
        renderer.onOutput(complete(2))

        when:
        renderer.onOutput(startExecuteRootBuild(7, 1, 0))
        renderer.onOutput(startExecuteBuild(8, 7, 4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############...............]> EXECUTING  0 running, 4 queued   [0ms]'

        when:
        renderer.onOutput(startExecuteTask(9, 8))
        renderer.onOutput(complete(9))
        renderer.onOutput(startExecuteTask(10, 8))
        renderer.onOutput(complete(10))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[######################........]> EXECUTING  0 running, 2 queued   [0ms]'
    }

    def "starts a new bar when a phase starts after the build finished"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(complete(1))

        when:
        renderer.onOutput(startConfigureRootBuild(2, 1, 2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> CONFIGURING  0 running, 2 queued   [0ms]'

        when:
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############...............]> CONFIGURING  0 running, 1 queued   [0ms]'
    }

    def "counts work run by nested builds during configuration"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startConfigureRootBuild(2, 1, 1))

        when:
        renderer.onOutput(startExecuteBuild(3, 2, 3))
        (4..6).each { id ->
            renderer.onOutput(startExecuteTask(id, 3))
            renderer.onOutput(complete(id))
        }
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[######################........]> CONFIGURING  0 running, 1 queued   [0ms]'
    }

    def "counts nested builds configured and run during initialization"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))

        when:
        renderer.onOutput(startConfigureBuild(2, 1, 1))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(startExecuteBuild(4, 1, 1))
        renderer.onOutput(startExecuteTask(5, 4))
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[##############################]> INITIALIZING  0 running, 0 queued   [0ms]'

        when:
        renderer.onOutput(startConfigureRootBuild(6, 1, 2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############...............]> CONFIGURING  0 running, 2 queued^  [0ms]'
    }

    def "ignores work reported after the build finished"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(complete(1))

        when:
        renderer.onOutput(startConfigureBuild(2, 1, 2))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(startExecuteBuild(4, 1, 2))
        renderer.onOutput(startExecuteTask(5, 4))
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................] WAITING'
    }

    def "drops projects that were not configured once execution starts"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startConfigureRootBuild(2, 1, 4))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))

        when:
        renderer.onOutput(startExecuteRootBuild(4, 1, 0))
        renderer.onOutput(startExecuteBuild(5, 4, 2))
        renderer.onOutput(startExecuteTask(6, 5))
        renderer.onOutput(complete(6))
        renderer.onOutput(startExecuteTask(7, 5))
        renderer.onOutput(complete(7))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[##############################]> EXECUTING  0 running, 0 queued   [0ms]'
    }

    def "counts projects configured after execution starts"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startConfigureRootBuild(2, 1, 4))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(startExecuteRootBuild(4, 1, 0))
        renderer.onOutput(startExecuteBuild(5, 4, 2))
        renderer.onOutput(startExecuteTask(6, 5))
        renderer.onOutput(complete(6))
        renderer.onOutput(startExecuteTask(7, 5))
        renderer.onOutput(complete(7))

        when:
        renderer.onOutput(startConfigureProject(8, 5))
        renderer.onOutput(complete(8))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[##############################]> EXECUTING  0 running, 0 queued   [0ms]'
    }

    def "starts a new bar for each build"() {
        given:
        currentTimeMs = 1000
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startConfigureRootBuild(2, 1, 1))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))

        when:
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................] WAITING'

        when:
        currentTimeMs += 5000
        renderer.onOutput(startRootBuildOperation(10))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................]> INITIALIZING  [0ms]'

        when:
        renderer.onOutput(startConfigureRootBuild(11, 10, 2))
        renderer.onOutput(startConfigureProject(12, 11))
        renderer.onOutput(complete(12))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############...............]> CONFIGURING  0 running, 1 queued   [0ms]'

        when:
        renderer.onOutput(complete(11))
        renderer.onOutput(complete(10))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................] WAITING'
    }

    def "ignores operations without a parent that are not builds"() {
        when:
        renderer.onOutput(startWithoutParent(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == ''

        when:
        renderer.onOutput(complete(1))
        renderer.onOutput(startRootBuildOperation(2))
        renderer.onOutput(complete(2))
        renderer.onOutput(startWithoutParent(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[..............................] WAITING'
    }

    def startRootBuildOperation(Long id) {
        new ProgressStartEvent(new OperationIdentifier(id), null, currentTimeMs, "category", "description", null, null, 0, true, new OperationIdentifier(id), BuildOperationCategory.RUN_BUILD)
    }

    def startWithoutParent(Long id) {
        new ProgressStartEvent(new OperationIdentifier(id), null, currentTimeMs, "category", "description", null, null, 0, true, new OperationIdentifier(id), BuildOperationCategory.UNCATEGORIZED)
    }

    def startConfigureRootBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.CONFIGURE_ROOT_BUILD, totalProgress)
    }

    def startConfigureBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.CONFIGURE_BUILD, totalProgress)
    }

    def startConfigureProject(Long id, Long parentId) {
        return start(id, parentId, BuildOperationCategory.CONFIGURE_PROJECT)
    }

    def startExecuteRootBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.RUN_MAIN_TASKS, totalProgress)
    }

    def startExecuteBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.RUN_WORK, totalProgress)
    }

    def startExecuteTask(Long id, Long parentId) {
        return start(id, parentId, BuildOperationCategory.TASK)
    }

    def startOther(Long id, Long parentId) {
        return start(id, parentId, BuildOperationCategory.UNCATEGORIZED)
    }

    def start(Long id, Long parentId, BuildOperationCategory category, int totalProgress = 0) {
        new ProgressStartEvent(new OperationIdentifier(id), new OperationIdentifier(parentId), currentTimeMs, "category", "description", null, null, totalProgress, true, new OperationIdentifier(id), category)
    }

    private ConsoleStub.TestableRedrawableLabel getStatusBar() {
        console.statusBar as ConsoleStub.TestableRedrawableLabel
    }
}
