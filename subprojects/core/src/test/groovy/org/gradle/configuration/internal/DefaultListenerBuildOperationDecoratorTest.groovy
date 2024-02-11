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
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.InternalListener
import org.gradle.internal.code.DefaultUserCodeApplicationContext
import org.gradle.internal.code.UserCodeApplicationId
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.service.scopes.Scopes
import spock.lang.Specification

class DefaultListenerBuildOperationDecoratorTest extends Specification {

    private static interface InternalProjectEvaluationListener extends ProjectEvaluationListener, InternalListener {}

    private static interface InternalTaskExecutionGraphListener extends TaskExecutionGraphListener, InternalListener {}

    private static interface Other {
        void completed()
    }

    private static interface ComboListener extends BuildListener, ProjectEvaluationListener, TaskExecutionGraphListener, Other {}

    def buildOperationExecutor = new TestBuildOperationExecutor()
    def context = new DefaultUserCodeApplicationContext()
    def decorator = new DefaultListenerBuildOperationDecorator(buildOperationExecutor, context)

    def 'ignores implementers of InternalListener'() {
        given:
        def action = Mock(InternalAction)
        def buildListener = new InternalBuildAdapter()
        def projectEvaluationListener = Mock(InternalProjectEvaluationListener)
        def graphListener = Mock(InternalTaskExecutionGraphListener)

        expect:
        decorator.decorate('foo', action) is action

        and:
        decorator.decorate('foo', BuildListener, buildListener) is buildListener
        decorator.decorateUnknownListener('foo', buildListener) is buildListener

        and:
        decorator.decorate('foo', ProjectEvaluationListener, projectEvaluationListener) is projectEvaluationListener
        decorator.decorateUnknownListener('foo', projectEvaluationListener) is projectEvaluationListener

        and:
        decorator.decorate('foo', TaskExecutionGraphListener, graphListener) is graphListener
        decorator.decorateUnknownListener('foo', graphListener) is graphListener
    }

    def 'ignores classes which do not implement any of the supported interfaces'() {
        given:
        def testListener = Mock(TestListener)

        expect:
        decorator.decorate('foo', TestListener, testListener) is testListener
        decorator.decorateUnknownListener('foo', testListener) is testListener
    }

    def 'decorates actions'() {
        given:
        def action = Mock(Action)
        def arg = new Object()
        def id
        def decoratedAction

        when:

        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedAction = decorator.decorate('foo', action)
        }

        then:
        !decoratedAction.is(action)

        when:
        decoratedAction.execute(arg)

        then:
        1 * action.execute(arg)

        and:
        verifyExpectedOp('foo', id)
    }

    def 'decorated action propagates exception'() {
        given:
        def action = Mock(Action)
        def failure = new RuntimeException()
        def arg = new Object()
        def id
        def decoratedAction
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedAction = decorator.decorate('foo', action)
        }

        when:
        decoratedAction.execute(arg)

        then:
        def e = thrown(RuntimeException)
        e.is(failure)

        and:
        1 * action.execute(arg) >> { throw failure }

        and:
        verifyExpectedOp('foo', id, failure)
    }

    def 'decorates closures of same single arity'() {
        given:
        def called = false
        def arg = new Object()
        def closure = { passedArg ->
            assert passedArg == arg
            called = true
        }
        def id
        def decoratedClosure

        when:
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedClosure = decorator.decorate('foo', closure)
        }

        then:
        !decoratedClosure.is(closure)

        when:
        decoratedClosure.call(arg)

        then:
        called

        and:
        verifyExpectedOp('foo', id)
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
        def id
        def decoratedClosure

        when:
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedClosure = decorator.decorate('foo', closure)
        }

        then:
        !decoratedClosure.is(closure)

        when:
        decoratedClosure.call(arg1, arg2)

        then:
        called

        and:
        verifyExpectedOp('foo', id)
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
        def id
        def decoratedClosure

        when:
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedClosure = decorator.decorate('foo', closure)
        }

        then:
        !decoratedClosure.is(closure)

        when:
        decoratedClosure.call(arg1, arg2)

        then:
        called

        and:
        verifyExpectedOp('foo', id)
    }

    def 'decorates closures of zero arity'() {
        given:
        def called = false
        def arg = new Object()
        def closure = { ->
            called = true
        }
        def id
        def decoratedClosure

        when:
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedClosure = decorator.decorate('foo', closure)
        }

        then:
        !decoratedClosure.is(closure)

        when:
        decoratedClosure.call(arg)

        then:
        called

        and:
        verifyExpectedOp('foo', id)
    }

    def 'decorated closure propagates exception'() {
        given:
        def failure = new RuntimeException()
        def arg = new Object()
        def closure = { passedArg ->
            throw failure
        }
        def id
        def decoratedClosure

        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedClosure = decorator.decorate('foo', closure)
        }

        when:
        decoratedClosure.call(arg)

        then:
        def e = thrown(RuntimeException)
        e.is(failure)

        and:
        verifyExpectedOp('foo', id, failure)
    }

    def 'decorates BuildListener listeners'() {
        given:
        def settingsEvaluatedArg = Mock(Settings)
        def projectsLoadedArg = Mock(Gradle)
        def projectsEvaluatedArg = Mock(Gradle)
        def buildFinishedArg = new BuildResult(null, null)
        def listener = Mock(BuildListener)
        def id
        def decoratedListener

        when:
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedListener = decorateAsObject ? decorator.decorateUnknownListener('foo', listener) as BuildListener : decorator.decorate('foo', BuildListener, listener)
        }

        then:
        !decoratedListener.is(listener)

        when:
        decoratedListener.settingsEvaluated(settingsEvaluatedArg)

        then:
        1 * listener.settingsEvaluated(settingsEvaluatedArg)

        and:
        verifyNoOp()

        when:
        decoratedListener.beforeSettings(settingsEvaluatedArg)

        then:
        1 * listener.beforeSettings(settingsEvaluatedArg)

        and:
        verifyNoOp()

        when:
        decoratedListener.projectsLoaded(projectsLoadedArg)

        then:
        1 * listener.projectsLoaded(projectsLoadedArg)

        and:
        verifyExpectedOp('foo', id)

        when:
        resetOps()
        decoratedListener.projectsEvaluated(projectsEvaluatedArg)

        then:
        1 * listener.projectsEvaluated(projectsEvaluatedArg)

        and:
        verifyExpectedOp('foo', id)

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

    def 'decorated BuildListener rethrows failures'() {
        given:
        def settingsEvaluatedArg = Mock(Settings)
        def projectsLoadedArg = Mock(Gradle)
        def projectsEvaluatedArg = Mock(Gradle)
        def buildFinishedArg = new BuildResult(null, null)
        def listener = Mock(BuildListener)
        def failure = new RuntimeException()
        def id
        def decoratedListener

        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedListener = decorator.decorate('foo', BuildListener, listener)
        }

        when:
        decoratedListener.settingsEvaluated(settingsEvaluatedArg)

        then:
        def e2 = thrown(RuntimeException)
        e2.is(failure)

        and:
        1 * listener.settingsEvaluated(settingsEvaluatedArg) >> { throw failure }

        and:
        verifyNoOp()

        when:
        decoratedListener.projectsLoaded(projectsLoadedArg)

        then:
        def e3 = thrown(RuntimeException)
        e3.is(failure)

        and:
        1 * listener.projectsLoaded(projectsLoadedArg) >> { throw failure }

        and:
        verifyExpectedOp('foo', id, failure)

        when:
        resetOps()
        decoratedListener.projectsEvaluated(projectsEvaluatedArg)

        then:
        def e4 = thrown(RuntimeException)
        e4.is(failure)

        and:
        1 * listener.projectsEvaluated(projectsEvaluatedArg) >> { throw failure }

        and:
        verifyExpectedOp('foo', id, failure)

        when:
        resetOps()
        decoratedListener.buildFinished(buildFinishedArg)

        then:
        def e5 = thrown(RuntimeException)
        e5.is(failure)

        and:
        1 * listener.buildFinished(buildFinishedArg) >> { throw failure }

        and:
        verifyNoOp()
    }

    def 'decorates ProjectEvaluationListener listeners'() {
        given:
        def beforeEvaluateArg = Mock(Project)
        def afterEvaluateArg1 = Mock(Project)
        def afterEvaluateArg2 = Mock(ProjectState)
        def listener = Mock(ProjectEvaluationListener)
        def id
        def decoratedListener

        when:
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedListener = decorateAsObject ? decorator.decorateUnknownListener('foo', listener) as ProjectEvaluationListener : decorator.decorate('foo', ProjectEvaluationListener, listener)
        }

        then:
        !decoratedListener.is(listener)

        when:
        decoratedListener.beforeEvaluate(beforeEvaluateArg)

        then:
        1 * listener.beforeEvaluate(beforeEvaluateArg)

        and:
        verifyExpectedOp('foo', id)

        when:
        resetOps()
        decoratedListener.afterEvaluate(afterEvaluateArg1, afterEvaluateArg2)

        then:
        1 * listener.afterEvaluate(afterEvaluateArg1, afterEvaluateArg2)

        and:
        verifyExpectedOp('foo', id)

        where:
        decorateAsObject << [true, false]
    }

    def 'decorates TaskExecutionGraphListener listeners'() {
        given:
        def arg = Mock(TaskExecutionGraph)
        def listener = Mock(TaskExecutionGraphListener)
        def id
        def decoratedListener

        when:
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedListener = decorateAsObject ? decorator.decorateUnknownListener('foo', listener) as TaskExecutionGraphListener : decorator.decorate('foo', TaskExecutionGraphListener, listener)
        }

        then:
        !decoratedListener.is(listener)

        when:
        decoratedListener.graphPopulated(arg)

        then:
        1 * listener.graphPopulated(arg)

        and:
        verifyExpectedOp('foo', id)

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
        def id
        def decoratedListener

        when:
        context.apply(Stub(UserCodeSource)) {
            id = it
            decoratedListener = decorator.decorateUnknownListener('foo', listener) as ComboListener
        }

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
        verifyExpectedOp('foo', id)

        when:
        resetOps()
        decoratedListener.beforeEvaluate(beforeEvaluateArg)

        then:
        1 * listener.beforeEvaluate(beforeEvaluateArg)

        and:
        verifyExpectedOp('foo', id)

        when:
        resetOps()
        decoratedListener.graphPopulated(graphPopulatedArg)

        then:
        1 * listener.graphPopulated(graphPopulatedArg)

        and:
        verifyExpectedOp('foo', id)

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
        def listenerManager = new DefaultListenerManager(Scopes.Build)
        def gradle = Mock(Gradle)
        boolean called = false
        def undecorated = new BuildAdapter() {
            @Override
            void projectsLoaded(Gradle ignored) {
                called = true
            }
        }
        def broadcast = listenerManager.getBroadcaster(BuildListener)
        def decorated

        when:
        context.apply(Stub(UserCodeSource)) {
            decorated = decorator.decorate('foo', BuildListener, undecorated)
        }
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

    private void verifyExpectedOp(String expectedRegistrationPoint, UserCodeApplicationId id, Throwable failure = null) {
        assert buildOperationExecutor.log.records.size() == 1
        def record = buildOperationExecutor.log.records.first()
        def op = record.descriptor
        assert op.displayName == "Execute $expectedRegistrationPoint listener"
        assert (op.details as ExecuteListenerBuildOperationType.Details).applicationId == id.longValue()
        assert record.failure == failure
    }
}
