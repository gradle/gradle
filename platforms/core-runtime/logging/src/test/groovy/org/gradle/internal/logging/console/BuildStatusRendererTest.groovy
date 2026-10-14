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
import org.gradle.internal.logging.events.BuildStartTimeEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Issue

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
        statusBar.display == "[...............] 0% INITIALIZING [0ms]"

        when:
        currentTimeMs += 1000
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == "[...............] 0% INITIALIZING [1s]"
    }

    @Issue("https://github.com/gradle/gradle/issues/39523")
    def "times the build from when it started"() {
        given:
        currentTimeMs = 5000

        when:
        renderer.onOutput(new BuildStartTimeEvent(2000))
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == "[...............] 0% INITIALIZING [3s]"
    }

    def "hides timer between build phases"() {
        given:
        def event1 = startRootBuildOperation(1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% INITIALIZING [0ms]'

        when:
        renderer.onOutput(complete(1, 'WAITING'))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
    }

    def "formats build"() {
        given:
        def event1 = startRootBuildOperation(1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% INITIALIZING [0ms]'

        when:
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
    }

    def "formats configuration phase"() {
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
        statusBar.display == '[...............] 0% CONFIGURING [0ms]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###............] 25% CONFIGURING [0ms]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(complete(4))
        renderer.onOutput(event5)
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###########....] 75% CONFIGURING [0ms]'
    }

    def "formats configuration phase with nested builds"() {
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
        statusBar.display == '[...............] 0% CONFIGURING [0ms]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#..............] 10% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#..............] 10% CONFIGURING [0ms]'
    }

    def "formats execution phase"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 4)
        def event3 = startExecuteTask(3, 2)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###............] 25% EXECUTING [0ms]'

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###............] 25% EXECUTING [0ms]'
    }

    def "formats execution phase with nested builds"() {
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
        statusBar.display == '[...............] 0% EXECUTING [0ms]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#..............] 10% EXECUTING [0ms]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#..............] 10% EXECUTING [0ms]'
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
        statusBar.display == '[...............] 0% CONFIGURING [0ms]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############] 100% CONFIGURING [0ms]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############] 100% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(event5)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms]'

        when:
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
    }

    def "counts building the work graph and storing and loading the configuration cache as configuration"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startConfigureRootBuild(2, 1, 1))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))

        when:
        renderer.onOutput(startBuildWorkGraph(4, 1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#######........] 50% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(startStoreConfigurationCache(5, 1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[##########.....] 66% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(5))
        renderer.onOutput(startLoadConfigurationCache(6, 1))
        renderer.onOutput(startBuildWorkGraph(7, 6))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###########....] 75% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(7))
        renderer.onOutput(complete(6))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############] 100% CONFIGURING [0ms]'

        when:
        renderer.onOutput(startExecuteRootBuild(8, 1, 1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms]'
    }

    def "configuration steps without total progress complete without growing the phase total"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        // 1 project plus 3 steps already counted in the total: work graph, store, and load
        renderer.onOutput(startConfigureRootBuild(2, 1, 4))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        expect:
        // 1 of 4 done: the project
        statusBar.display == '[###............] 25% CONFIGURING [0ms]'

        when:
        renderer.onOutput(startBuildWorkGraph(4, 1, 0))
        renderer.onOutput(updateNow())

        then:
        // Still 1 of 4: starting a step with no total progress doesn't change the total
        statusBar.display == '[###............] 25% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(startStoreConfigurationCache(5, 1, 0))
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())

        then:
        // 3 of 4 done: the project, the work graph, and the store
        statusBar.display == '[###########....] 75% CONFIGURING [0ms]'

        when:
        renderer.onOutput(startLoadConfigurationCache(6, 1, 0))
        renderer.onOutput(startBuildWorkGraph(7, 6))
        renderer.onOutput(complete(7))
        renderer.onOutput(complete(6))
        renderer.onOutput(updateNow())

        then:
        // 4 of 4 done: the load completes, and the work graph built inside it is not counted
        statusBar.display == '[###############] 100% CONFIGURING [0ms]'
    }

    def "configuration steps with total progress grow the phase total"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        // progress = 2: 1 project plus 1 expected CC store
        renderer.onOutput(startConfigureRootBuild(2, 1, 2))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(complete(3))

        when:
        renderer.onOutput(startBuildWorkGraph(4, 2))
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        // 2 of 3 done: the project and the work graph, which added 1 to the total
        statusBar.display == '[##########.....] 66% CONFIGURING [0ms]'

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(startStoreConfigurationCache(5, 1, 0))
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())

        then:
        // 3 of 3 done: the store was the step already counted in the total
        statusBar.display == '[###############] 100% CONFIGURING [0ms]'
    }

    def "shows loading phase when reusing the configuration cache"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))

        when:
        renderer.onOutput(startLoadConfigurationCache(2, 1))
        renderer.onOutput(startBuildWorkGraph(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% LOADING [0ms]'

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###############] 100% LOADING [0ms]'

        when:
        renderer.onOutput(startExecuteRootBuild(4, 1, 1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms]'
    }

    def "shows loading phase when reusing the configuration cache in a subsequent build"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(complete(1))

        when:
        renderer.onOutput(startRootBuildOperation(2))
        renderer.onOutput(startLoadConfigurationCache(3, 2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% LOADING [0ms]'
    }

    def "counts failed configuration cache load as progress"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startConfigureRootBuild(2, 1, 0))
        renderer.onOutput(complete(2))
        renderer.onOutput(startLoadConfigurationCache(3, 1))

        when:
        renderer.onOutput(new ProgressCompleteEvent(new OperationIdentifier(3), currentTimeMs, "FAILED", true))
        renderer.onOutput(startBuildWorkGraph(4, 1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#######........] 50% CONFIGURING [0ms]'
    }

    def "ignores building the work graph before configuration"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))

        when:
        renderer.onOutput(startBuildWorkGraph(2, 1))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% INITIALIZING [0ms]'
    }

    def "counts building the work graph of nested builds during configuration"() {
        given:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startConfigureRootBuild(2, 1, 2))

        when:
        renderer.onOutput(startBuildWorkGraph(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#####..........] 33% CONFIGURING [0ms]'
    }

    def startRootBuildOperation(Long id) {
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

    def startBuildWorkGraph(Long id, Long parentId, int totalProgress = 1) {
        return start(id, parentId, BuildOperationCategory.BUILD_WORK_GRAPH, totalProgress)
    }

    def startStoreConfigurationCache(Long id, Long parentId, int totalProgress = 1) {
        return start(id, parentId, BuildOperationCategory.STORE_CONFIGURATION_CACHE, totalProgress)
    }

    def startLoadConfigurationCache(Long id, Long parentId, int totalProgress = 1) {
        return start(id, parentId, BuildOperationCategory.LOAD_CONFIGURATION_CACHE, totalProgress)
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
