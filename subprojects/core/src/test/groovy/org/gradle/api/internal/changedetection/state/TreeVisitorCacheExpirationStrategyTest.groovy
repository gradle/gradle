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
import org.gradle.api.Task
import org.gradle.api.UncheckedIOException
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.TaskInputs
import org.gradle.api.tasks.TaskOutputs
import org.gradle.api.tasks.TaskState
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

@Requires(adhoc = { CachingTreeVisitor.CACHING_TREE_VISITOR_FEATURE_ENABLED })
@UsesNativeServices
class TreeVisitorCacheExpirationStrategyTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();

    def "listeners get registered in constructor and removed when stopping"() {
        given:
        CachingTreeVisitor cachingTreeVisitor = Mock()
        ListenerManager listenerManager = Mock()
        def buildListener
        def taskExecutionListener
        def taskExecutionGraphListener

        when:
        def treeVisitorCacheExpirationStrategy = new TreeVisitorCacheExpirationStrategy(cachingTreeVisitor, listenerManager, false)

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

    @Unroll
    def "cacheable tasks get resolved - scenario: #scenario"() {
        given:
        CachingTreeVisitor cachingTreeVisitor = Mock()
        DefaultListenerManager listenerManager = new DefaultListenerManager()
        def treeVisitorCacheExpirationStrategy = new TreeVisitorCacheExpirationStrategy(cachingTreeVisitor, listenerManager, false)
        TaskExecutionGraphListener taskExecutionGraphListener = listenerManager.getBroadcaster(TaskExecutionGraphListener)
        def taskExecutionGraph = Mock(TaskExecutionGraph)
        def allTasks = createTasksForScenario(scenario)
        def cacheableFilePaths = filePathsForScenario(scenario)

        when:
        taskExecutionGraphListener.graphPopulated(taskExecutionGraph)

        then:
        1 * taskExecutionGraph.getAllTasks() >> allTasks
        1 * cachingTreeVisitor.updateCacheableFilePaths(cacheableFilePaths)
        0 * _._

        where:
        scenario << ['none_cacheable', 'b_uses_a', 'a_and_b_use_same_input']
    }

    List<Task> createTasksForScenario(String scenario) {
        switch (scenario) {
            case 'none_cacheable':
                return [createTaskStub(":a", [file("a/input")], [file("a/output")]), createTaskStub(":b", [file("b/input")], [file("b/output")]), createTaskStub(":c", [file("c/input")], [file("c/output")])]
            case 'b_uses_a':
                return [createTaskStub(":a", [file("a/input")], [file("a/output")]), createTaskStub(":b", [file("a/output")], [file("b/output")])]
            case 'a_and_b_use_same_input':
                return [createTaskStub(":a", [file("shared/input")], [file("a/output")]), createTaskStub(":b", [file("shared/input")], [file("b/output")])]
        }
        throw new IllegalArgumentException("Unknown scenario")
    }

    Collection<String> filePathsForScenario(String scenario) {
        switch (scenario) {
            case 'none_cacheable':
                return []
            case 'b_uses_a':
                return [file("a/output").absolutePath]
            case 'a_and_b_use_same_input':
                return [file("shared/input").absolutePath]
        }
        throw new IllegalArgumentException("Unknown scenario")
    }

    def "cache gets flushed before executing task with unknown inputs"() {
        given:
        CachingTreeVisitor cachingTreeVisitor = Mock()
        DefaultListenerManager listenerManager = new DefaultListenerManager()
        def treeVisitorCacheExpirationStrategy = new TreeVisitorCacheExpirationStrategy(cachingTreeVisitor, listenerManager, false)
        TaskExecutionGraphListener taskExecutionGraphListener = listenerManager.getBroadcaster(TaskExecutionGraphListener)
        def taskExecutionGraph = Stub(TaskExecutionGraph)
        def allTasks = [createTaskStub(":a", [file("shared/input")], [file("a/output")]), createTaskStub(":b", [], [file("b/output")]), createTaskStub(":c", [file("shared/input")], [file("c/output")])]
        taskExecutionGraph.getAllTasks() >> allTasks
        def taskWithUnknownInputs = allTasks[1]
        TaskExecutionListener taskExecutionListener = listenerManager.getBroadcaster(TaskExecutionListener)

        when:
        taskExecutionGraphListener.graphPopulated(taskExecutionGraph)
        taskExecutionListener.beforeExecute(taskWithUnknownInputs)

        then:
        1 * cachingTreeVisitor.updateCacheableFilePaths(_)
        1 * cachingTreeVisitor.clearCache()
        0 * _._
    }

    def "cache gets flushed after executing task with unknown outputs"() {
        given:
        CachingTreeVisitor cachingTreeVisitor = Mock()
        DefaultListenerManager listenerManager = new DefaultListenerManager()
        def treeVisitorCacheExpirationStrategy = new TreeVisitorCacheExpirationStrategy(cachingTreeVisitor, listenerManager, false)
        TaskExecutionGraphListener taskExecutionGraphListener = listenerManager.getBroadcaster(TaskExecutionGraphListener)
        def taskExecutionGraph = Stub(TaskExecutionGraph)
        def allTasks = [createTaskStub(":a", [file("shared/input")], [file("a/output")]), createTaskStub(":b", [file("b/input")], []), createTaskStub(":c", [file("shared/input")], [file("c/output")])]
        taskExecutionGraph.getAllTasks() >> allTasks
        def taskWithUnknownOutputs = allTasks[1]
        TaskExecutionListener taskExecutionListener = listenerManager.getBroadcaster(TaskExecutionListener)

        when:
        taskExecutionGraphListener.graphPopulated(taskExecutionGraph)
        taskExecutionListener.afterExecute(taskWithUnknownOutputs, Stub(TaskState))

        then:
        1 * cachingTreeVisitor.updateCacheableFilePaths(_)
        1 * cachingTreeVisitor.clearCache()
        0 * _._
    }

    def "test that exception get swallowed and disables caching"() {
        given:
        CachingTreeVisitor cachingTreeVisitor = Mock()
        DefaultListenerManager listenerManager = new DefaultListenerManager()
        def treeVisitorCacheExpirationStrategy = new TreeVisitorCacheExpirationStrategy(cachingTreeVisitor, listenerManager)
        TaskExecutionGraphListener taskExecutionGraphListener = listenerManager.getBroadcaster(TaskExecutionGraphListener)
        def taskExecutionGraph = Stub(TaskExecutionGraph)
        def allTasks = createTasksWithIOExceptionThrown()
        taskExecutionGraph.getAllTasks() >> allTasks

        when:
        taskExecutionGraphListener.graphPopulated(taskExecutionGraph)

        then:
        noExceptionThrown()
        1 * cachingTreeVisitor.updateCacheableFilePaths(null)
        1 * cachingTreeVisitor.clearCache()
        0 * _._
    }

    private List<Task> createTasksWithIOExceptionThrown() {
        [createTaskStub(":a", [file("shared/input")], [file("a/output")]), createTaskStub(":b", [file("shared/input")], [file("b/output")], true)]
    }

    def "exception doesn't get swallowed when swallowing is disabled"() {
        given:
        CachingTreeVisitor cachingTreeVisitor = Mock()
        DefaultListenerManager listenerManager = new DefaultListenerManager()
        def treeVisitorCacheExpirationStrategy = new TreeVisitorCacheExpirationStrategy(cachingTreeVisitor, listenerManager, false)
        TaskExecutionGraphListener taskExecutionGraphListener = listenerManager.getBroadcaster(TaskExecutionGraphListener)
        def taskExecutionGraph = Stub(TaskExecutionGraph)
        def allTasks = createTasksWithIOExceptionThrown()
        taskExecutionGraph.getAllTasks() >> allTasks

        when:
        taskExecutionGraphListener.graphPopulated(taskExecutionGraph)

        then:
        thrown(UncheckedIOException)
        1 * cachingTreeVisitor.updateCacheableFilePaths(null)
        1 * cachingTreeVisitor.clearCache()
        0 * _._
    }


    Task createTaskStub(String path, List<File> inputs, List<File> outputs, boolean throwsException = false) {
        Task task = Stub(Task)
        task.getPath() >> path
        task.getActions() >> Stub(List) {
            isEmpty() >> false
        }

        TaskInputs taskInputs = Stub(TaskInputs)
        task.getInputs() >> taskInputs
        if (throwsException) {
            taskInputs.getFiles() >> { throw new UncheckedIOException(new IOException("Problem resolving input files")) }
        } else {
            taskInputs.getFiles() >> new SimpleFileCollection(inputs)
        }
        if (inputs.isEmpty()) {
            taskInputs.getHasInputs() >> false
        }

        TaskOutputs taskOutputs = Stub(TaskOutputs)
        task.getOutputs() >> taskOutputs
        taskOutputs.getFiles() >> new SimpleFileCollection(outputs)
        if (outputs.isEmpty()) {
            taskOutputs.getHasOutput() >> false
        }

        task
    }

    TestFile file(Object... path) {
        testDir.file(path)
    }

}
