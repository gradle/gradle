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
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier

class BuildStatusRendererTest extends OutputSpecification {
    def listener = Mock(OutputEventListener)
    def console = new ConsoleStub()
    def consoleMetaData = Mock(ConsoleMetaData)
    // Estimated duration of each task, unless a test says otherwise
    static final long TASK_MILLIS = 3000
    long currentTimeMs
    def renderer = new BuildStatusRenderer(listener, console.statusBar, console, consoleMetaData, new RecordedWorkDurationEstimator())

    @Override
    UpdateNowEvent updateNow() {
        return new UpdateNowEvent(currentTimeMs)
    }

    ProgressCompleteEvent complete(Long id, status = 'STATUS') {
        new ProgressCompleteEvent(new OperationIdentifier(id), currentTimeMs, status, false)
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
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 12s ETA]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 12s ETA]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###............] 25% EXECUTING [0ms ELAPSED | 9s ETA]'

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###............] 25% EXECUTING [0ms ELAPSED | 9s ETA]'
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
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 30s ETA]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 30s ETA]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#..............] 10% EXECUTING [0ms ELAPSED | 27s ETA]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#..............] 10% EXECUTING [0ms ELAPSED | 27s ETA]'
    }

    def "counts down estimate of all scheduled work"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 4)
        def event3 = startExecuteTask(3, 2)
        def event4 = startExecuteTask(4, 2)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        // 4 scheduled tasks * 3s
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 12s ETA]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        // 12s of work / 2 parallel
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 6s ETA]'

        when:
        currentTimeMs += 1000
        renderer.onOutput(updateNow())

        then:
        // (12s - 1s used by each running task) / 2 parallel
        statusBar.display == '[...............] 0% EXECUTING [1s ELAPSED | 5s ETA]'

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        // (3 remaining tasks * 3s - 1s used by running task) / 2 parallel
        statusBar.display == '[###............] 25% EXECUTING [1s ELAPSED | 4s ETA]'

        when:
        currentTimeMs += 3000
        renderer.onOutput(updateNow())

        then:
        // Running task has overrun its estimate, so the estimate stalls
        statusBar.display == '[###............] 25% EXECUTING [4s ELAPSED | 3s ETA]'

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#######........] 50% EXECUTING [4s ELAPSED | 3s ETA]'

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
    }

    def "estimate includes the longest chain of dependent work"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 0)
        // Tasks a and b take 3s each, and a slow task taking 13s runs after both
        def event3 = startExecuteBuild(3, 2, 3, 19000, 16000)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(startExecuteTask(4, 3, 3000, 16000))
        renderer.onOutput(startExecuteTask(5, 3, 3000, 16000))
        renderer.onOutput(updateNow())

        then:
        // The chain through the slow task outweighs 19s of work across 2 parallel tasks
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 16s ETA]'

        when:
        currentTimeMs += 2000
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [2s ELAPSED | 14s ETA]'

        when:
        currentTimeMs += 1000
        renderer.onOutput(complete(4))
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())

        then:
        // The slow task has not started yet, but must still run
        statusBar.display == '[##########.....] 66% EXECUTING [3s ELAPSED | 13s ETA]'

        when:
        renderer.onOutput(startExecuteTask(6, 3, 13000, 13000))
        currentTimeMs += 7000
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[##########.....] 66% EXECUTING [10s ELAPSED | 6s ETA]'
    }

    def "estimate drops when work finishes earlier than estimated"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 0)
        // A chain of 2 tasks taking 3s each
        def event3 = startExecuteBuild(3, 2, 2, 6000, 6000)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(startExecuteTask(4, 3, 3000, 6000))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 6s ETA]'

        when:
        currentTimeMs += 500
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        // Only the second task remains
        statusBar.display == '[#######........] 50% EXECUTING [500ms ELAPSED | 3s ETA]'
    }

    def "estimate drops when the work after a completed work item finishes earlier than estimated"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 0)
        // A quick task, then a task estimated at 3s that is up-to-date, then a task taking 2s
        def event3 = startExecuteBuild(3, 2, 3, 5000, 5000)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(startExecuteTask(4, 3, 0, 5000))
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[#####..........] 33% EXECUTING [0ms ELAPSED | 5s ETA]'

        when:
        currentTimeMs += 100
        renderer.onOutput(startExecuteTask(5, 3, 3000, 5000))
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())

        then:
        // Only the last task remains
        statusBar.display == '[##########.....] 66% EXECUTING [100ms ELAPSED | 2s ETA]'
    }

    def "estimate includes the work that follows a work item waiting on a nested build"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 0)
        // A task taking 3s runs a nested build, and a slow task taking 13s runs after it
        def event3 = startExecuteBuild(3, 2, 2, 16000, 16000)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(startExecuteTask(4, 3, 3000, 16000))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 16s ETA]'

        when:
        currentTimeMs += 3000
        // The nested build runs 2 tasks of 3s, one after the other
        renderer.onOutput(startExecuteBuild(5, 4, 2, 6000, 6000))
        renderer.onOutput(startExecuteTask(6, 5, 3000, 6000))
        renderer.onOutput(updateNow())

        then:
        // 6s of nested work, then the slow task
        statusBar.display == '[...............] 0% EXECUTING [3s ELAPSED | 19s ETA]'

        when:
        currentTimeMs += 3000
        renderer.onOutput(complete(6))
        renderer.onOutput(startExecuteTask(7, 5, 3000, 3000))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[###............] 25% EXECUTING [6s ELAPSED | 16s ETA]'
    }

    def "estimate increases only when more work is scheduled"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 4)
        def event3 = startExecuteTask(3, 2)
        def event4 = startExecuteBuild(4, 2, 2)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        currentTimeMs += 1000
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [1s ELAPSED | 11s ETA]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        // 2 more tasks * 3s
        statusBar.display == '[...............] 0% EXECUTING [1s ELAPSED | 17s ETA]'
    }

    def "ignores scheduling events without work"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 0)
        def event3 = startExecuteBuild(3, 2, 4)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 12s ETA]'
    }

    def "estimate is unavailable once any scheduled work has no timing data"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 4)
        def event3 = startExecuteBuildWithoutEstimate(3, 2, 2)
        def event4 = startExecuteTask(4, 3)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 12s ETA]'

        when:
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | ESTIMATE UNAVAILABLE]'

        when:
        renderer.onOutput(event4)
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[##.............] 16% EXECUTING [0ms ELAPSED | ESTIMATE UNAVAILABLE]'
    }

    def "work waiting on a nested build does not count as running work"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 2)
        def event3 = startExecuteTask(3, 2)
        def event4 = startExecuteTask(4, 2)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        // 2 scheduled tasks * 3s / 2 parallel
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 3s ETA]'

        when:
        currentTimeMs += 3000
        // Each task runs a nested build of 2 more tasks, below an intermediate operation
        renderer.onOutput(startOther(5, 3))
        renderer.onOutput(startExecuteBuild(6, 5, 2))
        renderer.onOutput(startOther(7, 4))
        renderer.onOutput(startExecuteBuild(8, 7, 2))
        renderer.onOutput(startExecuteTask(9, 6))
        renderer.onOutput(startExecuteTask(10, 8))
        renderer.onOutput(updateNow())

        then:
        // 4 nested tasks * 3s / 2 parallel, as the waiting tasks no longer count as running
        statusBar.display == '[...............] 0% EXECUTING [3s ELAPSED | 6s ETA]'

        when:
        currentTimeMs += 1000
        renderer.onOutput(updateNow())

        then:
        // Counts down with the clock
        statusBar.display == '[...............] 0% EXECUTING [4s ELAPSED | 5s ETA]'

        when:
        renderer.onOutput(complete(9))
        renderer.onOutput(complete(6))
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        // Completing the waiting task does not count it twice
        statusBar.display == '[#####..........] 33% EXECUTING [4s ELAPSED | 4s ETA]'
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
        statusBar.display == '[...............] 0% EXECUTING [0ms ELAPSED | 3s ETA]'

        when:
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
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

    // Schedules independent tasks, so the longest chain is a single task
    def startExecuteRootBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.RUN_MAIN_TASKS, totalProgress, TASK_MILLIS * totalProgress, TASK_MILLIS)
    }

    def startExecuteBuild(Long id, Long parentId, int totalProgress, long durationMillis = TASK_MILLIS * totalProgress, long criticalPathMillis = TASK_MILLIS) {
        return start(id, parentId, BuildOperationCategory.RUN_WORK, totalProgress, durationMillis, criticalPathMillis)
    }

    def startExecuteBuildWithoutEstimate(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.RUN_WORK, totalProgress)
    }

    def startExecuteTask(Long id, Long parentId, long durationMillis = TASK_MILLIS, long criticalPathMillis = durationMillis) {
        return start(id, parentId, BuildOperationCategory.TASK, 0, durationMillis, criticalPathMillis)
    }

    def startOther(Long id, Long parentId) {
        return start(id, parentId, BuildOperationCategory.UNCATEGORIZED)
    }

    def start(Long id, Long parentId, BuildOperationCategory category, int totalProgress = 0, Long durationMillis = null, Long criticalPathMillis = null) {
        new ProgressStartEvent(new OperationIdentifier(id), new OperationIdentifier(parentId), currentTimeMs, "category", "description", null, null, totalProgress, true, new OperationIdentifier(id), category, durationMillis, criticalPathMillis)
    }

    private ConsoleStub.TestableRedrawableLabel getStatusBar() {
        console.statusBar as ConsoleStub.TestableRedrawableLabel
    }
}
