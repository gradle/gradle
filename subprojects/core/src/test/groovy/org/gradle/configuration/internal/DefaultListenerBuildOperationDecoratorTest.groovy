/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.configuration.internal

import org.gradle.BuildAdapter
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.InternalAction
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.testing.TestListener
import org.gradle.initialization.BuildCompletionListener
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.InternalListener
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification
import spock.lang.Unroll

class DefaultListenerBuildOperationDecoratorTest extends Specification {

    private static final long APPLICATION_ID = 3
    private static interface InternalProjectEvaluationListener extends ProjectEvaluationListener, InternalListener {}
    private static interface InternalTaskExecutionGraphListener extends TaskExecutionGraphListener, InternalListener {}
    private static interface ComboListener extends BuildListener, ProjectEvaluationListener, TaskExecutionGraphListener, BuildCompletionListener {}

    def buildOperationExecutor = new TestBuildOperationExecutor()
    DefaultListenerBuildOperationDecorator decorator = new DefaultListenerBuildOperationDecorator(buildOperationExecutor)

    def 'ignores implementors of InternalListener'() {
        given:
        def action = Mock(InternalAction)
        def buildListener = new InternalBuildAdapter()
        def projectEvaluationListener = Mock(InternalProjectEvaluationListener)
        def graphListener = Mock(InternalTaskExecutionGraphListener)
        decorator.startApplication(APPLICATION_ID)

        expect:
        decorator.decorate('foo', action) is action

        and:
        decorator.decorate(BuildListener, buildListener) is buildListener
        decorator.decorateUnknownListener(buildListener) is buildListener

        and:
        decorator.decorate(ProjectEvaluationListener, projectEvaluationListener) is projectEvaluationListener
        decorator.decorateUnknownListener(projectEvaluationListener) is projectEvaluationListener

        and:
        decorator.decorate(TaskExecutionGraphListener, graphListener) is graphListener
        decorator.decorateUnknownListener(graphListener) is graphListener
    }

    def 'ignores classes which do not implement any of the supported interfaces'() {
        given:
        def testListener = Mock(TestListener)

        expect:
        decorator.decorate(TestListener, testListener) is testListener
        decorator.decorateUnknownListener(testListener) is testListener
    }

    def 'decorates actions'() {
        given:
        def action = Mock(Action)
        def arg = new Object()
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedAction = decorator.decorate('foo', action)

        then:
        !decoratedAction.is(action)

        when:
        decoratedAction.execute(arg)

        then:
        1 * action.execute(arg)

        and:
        verifyExpectedOp('foo')
    }

    def 'decorates closures of same single arity'() {
        given:
        def called = false
        def arg = new Object()
        def closure = { passedArg ->
            assert passedArg == arg
            called = true
        }
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedClosure = decorator.decorate('foo', closure)

        then:
        !decoratedClosure.is(closure)

        when:
        decoratedClosure.call(arg)

        then:
        called

        and:
        verifyExpectedOp('foo')
    }

    def 'decorates closures of same multiple arity'() {
        given:
        def called = false
        def arg1 = new Object()
        def arg2 = new Object()
        def closure = { passedArg1, passedArg2 ->
            assert passedArg1 == arg1
            assert passedArg2 == arg2
            called = true
        }
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedClosure = decorator.decorate('foo', closure)

        then:
        !decoratedClosure.is(closure)

        when:
        decoratedClosure.call(arg1, arg2)

        then:
        called

        and:
        verifyExpectedOp('foo')
    }

    def 'decorates closures of lower arity'() {
        given:
        def called = false
        def arg1 = new Object()
        def arg2 = new Object()
        def closure = { passedArg ->
            assert passedArg == arg1
            called = true
        }
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedClosure = decorator.decorate('foo', closure)

        then:
        !decoratedClosure.is(closure)

        when:
        decoratedClosure.call(arg1, arg2)

        then:
        called

        and:
        verifyExpectedOp('foo')
    }

    def 'decorates closures of zero arity'() {
        given:
        def called = false
        def arg = new Object()
        def closure = { ->
            called = true
        }
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedClosure = decorator.decorate('foo', closure)

        then:
        !decoratedClosure.is(closure)

        when:
        decoratedClosure.call(arg)

        then:
        called

        and:
        verifyExpectedOp('foo')
    }

    @Unroll
    def 'decorates BuildListener listeners'() {
        given:
        def buildStartedArg = Mock(Gradle)
        def settingsEvaluatedArg = Mock(Settings)
        def projectsLoadedArg = Mock(Gradle)
        def projectsEvaluatedArg = Mock(Gradle)
        def buildFinishedArg = new BuildResult(null, null)
        def listener = Mock(BuildListener)
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedListener = decorateAsObject ? decorator.decorateUnknownListener(listener) as BuildListener : decorator.decorate(BuildListener, listener)

        then:
        !decoratedListener.is(listener)

        when:
        decoratedListener.buildStarted(buildStartedArg)

        then:
        1 * listener.buildStarted(buildStartedArg)

        and:
        verifyNoOp()

        when:
        decoratedListener.settingsEvaluated(settingsEvaluatedArg)

        then:
        1 * listener.settingsEvaluated(settingsEvaluatedArg)

        and:
        verifyNoOp()

        when:
        decoratedListener.projectsLoaded(projectsLoadedArg)

        then:
        1 * listener.projectsLoaded(projectsLoadedArg)

        and:
        verifyExpectedOp('projectsLoaded')

        when:
        resetOps()
        decoratedListener.projectsEvaluated(projectsEvaluatedArg)

        then:
        1 * listener.projectsEvaluated(projectsEvaluatedArg)

        and:
        verifyExpectedOp('projectsEvaluated')

        when:
        resetOps()
        decoratedListener.buildFinished(buildFinishedArg)

        then:
        1 * listener.buildFinished(buildFinishedArg)

        and:
        verifyNoOp()

        where:
        decorateAsObject << [true, false]
    }

    @Unroll
    def 'decorates ProjectEvaluationListener listeners'() {
        given:
        def beforeEvaluateArg = Mock(Project)
        def afterEvaluateArg1 = Mock(Project)
        def afterEvaluateArg2 = Mock(ProjectState)
        def listener = Mock(ProjectEvaluationListener)
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedListener = decorateAsObject ? decorator.decorateUnknownListener(listener) as ProjectEvaluationListener : decorator.decorate(ProjectEvaluationListener, listener)

        then:
        !decoratedListener.is(listener)

        when:
        decoratedListener.beforeEvaluate(beforeEvaluateArg)

        then:
        1 * listener.beforeEvaluate(beforeEvaluateArg)

        and:
        verifyExpectedOp('beforeEvaluate')

        when:
        resetOps()
        decoratedListener.afterEvaluate(afterEvaluateArg1, afterEvaluateArg2)

        then:
        1 * listener.afterEvaluate(afterEvaluateArg1, afterEvaluateArg2)

        and:
        verifyExpectedOp('afterEvaluate')

        where:
        decorateAsObject << [true, false]
    }

    @Unroll
    def 'decorates TaskExecutionGraphListener listeners'() {
        given:
        def arg = Mock(TaskExecutionGraph)
        def listener = Mock(TaskExecutionGraphListener)
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedListener = decorateAsObject ? decorator.decorateUnknownListener(listener) as TaskExecutionGraphListener : decorator.decorate(TaskExecutionGraphListener, listener)

        then:
        !decoratedListener.is(listener)

        when:
        decoratedListener.graphPopulated(arg)

        then:
        1 * listener.graphPopulated(arg)

        and:
        verifyExpectedOp('graphPopulated')

        where:
        decorateAsObject << [true, false]
    }

    def 'decorates listeners that are a combination of listener interfaces'() {
        given:
        def settingsEvaluatedArg = Mock(Settings)
        def projectsLoadedArg = Mock(Gradle)
        def beforeEvaluateArg = Mock(Project)
        def graphPopulatedArg = Mock(TaskExecutionGraph)
        def listener = Mock(ComboListener)
        decorator.startApplication(APPLICATION_ID)

        when:
        def decoratedListener = decorator.decorateUnknownListener(listener) as ComboListener

        then:
        !decoratedListener.is(listener)

        when:
        decoratedListener.settingsEvaluated(settingsEvaluatedArg)

        then:
        1 * listener.settingsEvaluated(settingsEvaluatedArg)

        and:
        verifyNoOp()

        when:
        decoratedListener.projectsLoaded(projectsLoadedArg)

        then:
        1 * listener.projectsLoaded(projectsLoadedArg)

        and:
        verifyExpectedOp('projectsLoaded')

        when:
        resetOps()
        decoratedListener.beforeEvaluate(beforeEvaluateArg)

        then:
        1 * listener.beforeEvaluate(beforeEvaluateArg)

        and:
        verifyExpectedOp('beforeEvaluate')

        when:
        resetOps()
        decoratedListener.graphPopulated(graphPopulatedArg)

        then:
        1 * listener.graphPopulated(graphPopulatedArg)

        and:
        verifyExpectedOp('graphPopulated')

        when:
        resetOps()
        decoratedListener.completed()

        then:
        1 * listener.completed()

        and:
        verifyNoOp()
    }

    def 'decorated listeners can be removed from listener manager'() {
        given:
        def listenerManager = new DefaultListenerManager()
        def gradle = Mock(Gradle)
        boolean called = false
        def undecorated = new BuildAdapter() {
            @Override
            void projectsLoaded(Gradle ignored) {
                called = true
            }
        }
        def broadcast = listenerManager.getBroadcaster(BuildListener)

        when:
        def decorated = decorator.decorate(BuildListener, undecorated)
        listenerManager.addListener(decorated)
        broadcast.projectsLoaded(gradle)

        then:
        called

        when:
        called = false
        listenerManager.removeListener(decorated)
        broadcast.projectsLoaded(gradle)

        then:
        !called
    }

    private void resetOps() {
        buildOperationExecutor.reset()
    }

    private void verifyNoOp() {
        assert buildOperationExecutor.operations.empty
    }

    private void verifyExpectedOp(String expectedName, long expectedApplicationId = APPLICATION_ID) {
        assert buildOperationExecutor.operations.size() == 1
        def op = buildOperationExecutor.operations.first()
        assert op.displayName == "Execute $expectedName listener"
        assert (op.details as ExecuteListenerBuildOperationType.Details).applicationId == expectedApplicationId
    }
}
