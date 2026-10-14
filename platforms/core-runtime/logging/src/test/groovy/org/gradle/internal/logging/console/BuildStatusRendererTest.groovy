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
import org.gradle.internal.logging.events.EndOutputEvent
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
    long currentTimeMs
    long clientStartTime = 0
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

    def "reports program status transitions and execution progress"() {
        given:
        def output = new ByteArrayOutputStream()
        def renderer = new BuildStatusRenderer(listener, console.statusBar, console, consoleMetaData, new ProgramStatusReporter(output, clientStartTime))

        when:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(updateNow())
        renderer.onOutput(startConfigureRootBuild(2, 1, 1))
        renderer.onOutput(startConfigureProject(3, 2))
        renderer.onOutput(updateNow())
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        programStatusReports(output) == ["state=working:app=gradle"]

        when:
        renderer.onOutput(startExecuteRootBuild(4, 1, 4))
        renderer.onOutput(updateNow())
        renderer.onOutput(startExecuteTask(5, 4))
        renderer.onOutput(updateNow())
        renderer.onOutput(complete(5))
        renderer.onOutput(updateNow())
        currentTimeMs += 1000
        renderer.onOutput(updateNow())

        then:
        programStatusReports(output) == ["state=working:app=gradle", "state=working:app=gradle:progress=0", "state=working:app=gradle:progress=25"]

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(completeRoot(1, false))
        renderer.onOutput(updateNow())
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
        programStatusReports(output).last() == 'state=done:app=gradle:msg=BUILD SUCCESSFUL in 1s'
        programStatusReports(output).size() == 4
    }

    def "ignores root operations after the build has ended"() {
        given:
        def output = new ByteArrayOutputStream()
        def renderer = new BuildStatusRenderer(listener, console.statusBar, console, consoleMetaData, new ProgramStatusReporter(output, clientStartTime))
        currentTimeMs = 1000

        when:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(completeRoot(1, true))
        // For example, the cleanup of unused caches
        renderer.onOutput(startRootBuildOperation(2))
        renderer.onOutput(updateNow())
        renderer.onOutput(complete(2))
        renderer.onOutput(new EndOutputEvent())

        then:
        statusBar.display == '[...............] 0% WAITING'
        programStatusReports(output) == ['state=error:app=gradle:msg=BUILD FAILED in 1s']
    }

    def "measures the first build from the start of the client JVM"() {
        given:
        def output = new ByteArrayOutputStream()
        def renderer = new BuildStatusRenderer(listener, console.statusBar, console, consoleMetaData, new ProgramStatusReporter(output, 0))
        currentTimeMs = 1000

        when:
        renderer.onOutput(startRootBuildOperation(1))
        currentTimeMs += 2000
        renderer.onOutput(completeRoot(1, false))
        renderer.onOutput(new EndOutputEvent())

        then:
        programStatusReports(output) == ['state=done:app=gradle:msg=BUILD SUCCESSFUL in 3s']
    }

    def "starts over for each build of a continuous build"() {
        given:
        def output = new ByteArrayOutputStream()
        def renderer = new BuildStatusRenderer(listener, console.statusBar, console, consoleMetaData, new ProgramStatusReporter(output, clientStartTime))
        currentTimeMs = 1000

        when:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startExecuteRootBuild(2, 1, 1))
        renderer.onOutput(startExecuteTask(3, 2))
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(completeRoot(1, false))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
        programStatusReports(output) == [done1]

        when:
        currentTimeMs += 5000
        renderer.onOutput(startRootBuildOperation(4))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
        programStatusReports(output) == [done1]

        when:
        renderer.onOutput(startConfigureRootBuild(5, 4, 1))
        currentTimeMs += 1000
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% CONFIGURING [1s]'
        programStatusReports(output) == [done1, "state=working:app=gradle"]

        when:
        renderer.onOutput(complete(5))
        renderer.onOutput(startExecuteRootBuild(6, 4, 1))
        renderer.onOutput(startExecuteTask(7, 6))
        renderer.onOutput(new ProgressCompleteEvent(new OperationIdentifier(7), currentTimeMs, "FAILED", true))
        renderer.onOutput(complete(6))
        currentTimeMs += 2000
        renderer.onOutput(completeRoot(4, true))
        renderer.onOutput(updateNow())

        then:
        statusBar.display == '[...............] 0% WAITING'
        // Each build is measured from its own start
        programStatusReports(output) == [done1, "state=working:app=gradle", 'state=error:app=gradle:msg=BUILD FAILED in 3s']

        where:
        done1 = 'state=done:app=gradle:msg=BUILD SUCCESSFUL in 1s'
    }

    def "reports program status error when the root build operation fails"() {
        given:
        def output = new ByteArrayOutputStream()
        def renderer = new BuildStatusRenderer(listener, console.statusBar, console, consoleMetaData, new ProgramStatusReporter(output, clientStartTime))

        when:
        renderer.onOutput(startRootBuildOperation(1))
        renderer.onOutput(startExecuteRootBuild(2, 1, 1))
        renderer.onOutput(startExecuteTask(3, 2))
        renderer.onOutput(new ProgressCompleteEvent(new OperationIdentifier(3), currentTimeMs, "FAILED", true))
        renderer.onOutput(updateNow())
        renderer.onOutput(complete(2))
        renderer.onOutput(completeRoot(1, true))
        renderer.onOutput(new EndOutputEvent())

        then:
        programStatusReports(output) == ["state=working:app=gradle:progress=100", 'state=error:app=gradle:msg=BUILD FAILED in 0ms']
    }

    def "does not report program status before the build starts"() {
        given:
        def output = new ByteArrayOutputStream()
        def renderer = new BuildStatusRenderer(listener, console.statusBar, console, consoleMetaData, new ProgramStatusReporter(output, clientStartTime))

        when:
        renderer.onOutput(event("message"))
        renderer.onOutput(updateNow())

        then:
        output.size() == 0
    }

    private static List<String> programStatusReports(ByteArrayOutputStream output) {
        return (output.toString("US-ASCII") =~ '\u001B\\]7501;([^\u001B]*)\u001B\\\\').collect { ProgramStatusReporterTest.decodeMessage(it[1] as String) }
    }

    def completeRoot(Long id, boolean failed) {
        new ProgressCompleteEvent(new OperationIdentifier(id), currentTimeMs, "", failed)
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
