/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.configuration.project

import org.gradle.StartParameter
import org.gradle.api.BuildCancelledException
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateInternal
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.Path
import spock.lang.Specification

import java.util.function.Consumer

class LifecycleProjectEvaluatorTest extends Specification {

    private project = Mock(ProjectInternal)
    private gradle = Mock(GradleInternal)
    private listener = Mock(ProjectEvaluationListener)
    private delegate = Mock(ProjectEvaluator)
    private cancellationToken = Mock(BuildCancellationToken)
    private buildOperationExecutor = new TestBuildOperationExecutor()
    private evaluator = new LifecycleProjectEvaluator(buildOperationExecutor, delegate, cancellationToken)
    private state = new ProjectStateInternal()
    private mutationState = Mock(ProjectState)

    final RuntimeException failure1 = new RuntimeException()
    final RuntimeException failure2 = new RuntimeException()

    void setup() {
        project.getProjectEvaluationBroadcaster() >> listener
        project.displayName >> "<project>"
        project.gradle >> gradle
        gradle.getIdentityPath() >> Path.path(":")
        gradle.startParameter >> new StartParameter()
        project.projectPath >> Path.path(":project1")
        project.path >> project.projectPath.toString()
        project.identityPath >> Path.path(":project1")
        project.stepEvaluationListener(listener, _) >> { listener, step ->
            step.execute(listener)
            null
        }
        project.getOwner() >> mutationState
        mutationState.applyToMutableState(_) >> { Consumer consumer -> consumer.accept(project) }
    }

    void "nothing happens if project was already configured"() {
        given:
        state.configured()

        when:
        evaluate()

        then:
        state.executed
        0 * delegate._

        and:
        operations.empty
    }

    void "nothing happens if project is being configured now"() {
        given:
        state.toBeforeEvaluate()

        when:
        evaluate()

        then:
        state.configuring
        0 * delegate._

        and:
        operations.empty
    }

    void "fails when build has been cancelled"() {
        when:
        evaluate()

        then:
        1 * cancellationToken.cancellationRequested >> true
        0 * delegate._

        and:
        thrown(BuildCancelledException)
    }

    void "evaluates the project firing all necessary listeners and updating the state"() {
        when:
        evaluate()

        then:
        1 * listener.beforeEvaluate(project) >> {
            assert !state.unconfigured
            assert !state.executed
            assert state.configuring
        }

        then:
        1 * delegate.evaluate(project, state) >> {
            assert !state.unconfigured
            assert !state.executed
            assert state.configuring

        }

        then:
        1 * listener.afterEvaluate(project, state) >> {
            assert !state.unconfigured
            assert state.executed
            assert state.configuring
        }

        and:
        state.executed

        and:
        operations.size() == 3
        assertConfigureOp(operations[0])
        assertBeforeEvaluateOp(operations[1])
        assertAfterEvaluateOp(operations[2])
    }

    void "notifies listeners and updates state on evaluation failure"() {
        when:
        1 * delegate.evaluate(project, state) >> { throw failure1 }
        1 * listener.afterEvaluate(project, state)

        then:
        failsWithCause(failure1)

        and:
        operations.size() == 3
        assertConfigureOp(operations[0], failure1)
        assertBeforeEvaluateOp(operations[1])
        assertAfterEvaluateOp(operations[2])
    }

    void "updates state and does not delegate when beforeEvaluate action fails"() {
        when:
        1 * listener.beforeEvaluate(project) >> { throw failure1 }

        and:
        failsWithCause(failure1)

        then:
        0 * delegate._
        0 * listener._

        and:
        operations.size() == 2
        assertConfigureOp(operations[0], failure1)
        assertBeforeEvaluateOp(operations[1], failure1)
    }

    void "updates state when afterEvaluate action fails"() {
        when:
        1 * listener.afterEvaluate(project, state) >> { throw failure1 }

        then:
        failsWithCause(failure1)

        and:
        operations.size() == 3
        assertConfigureOp(operations[0], failure1)
        assertBeforeEvaluateOp(operations[1])
        assertAfterEvaluateOp(operations[2], failure1)
    }

    void "notifies listeners and updates state on evaluation failure even if afterEvaluate fails"() {
        when:
        1 * delegate.evaluate(project, state) >> { throw failure1 }

        and:
        1 * listener.afterEvaluate(project, state) >> { throw failure2 }

        then:
        failsWithCause(failure1, failure2)

        and:
        operations.size() == 3
        assertConfigureOp(operations[0], failure1)
        assertBeforeEvaluateOp(operations[1])
        assertAfterEvaluateOp(operations[2], failure2)
    }

    private void failsWithCause(RuntimeException... causes) {
        try {
            evaluate()
            throw new AssertionError("Expected to fail")
        } catch (ProjectConfigurationException e) {
            assert e.message == "A problem occurred configuring <project>."
            assert e.causes == causes as List

            assert state.executed
            assert state.failure.is(e)
        } catch (Exception e) {
            throw new AssertionError("Unexpected type of failure", e)
        }
    }

    private void evaluate() {
        evaluator.evaluate(project, state)
    }

    static void assertConfigureOp(TestBuildOperationExecutor.Log.Record op, Throwable failureCause = null) {
        assert op.descriptor.name == 'Configure project :project1'
        assert op.descriptor.displayName == 'Configure project :project1'

        def details = op.descriptor.details as ConfigureProjectBuildOperationType.Details
        details.projectPath == ":project1"
        details.buildPath == ":"

        assertOpFailureOrNot(op, ConfigureProjectBuildOperationType.Result, failureCause)
    }

    static void assertBeforeEvaluateOp(TestBuildOperationExecutor.Log.Record op, Throwable failureCause = null) {
        assert op.descriptor.name == 'Notify beforeEvaluate listeners of :project1'
        assert op.descriptor.displayName == 'Notify beforeEvaluate listeners of :project1'

        def details = op.descriptor.details as NotifyProjectBeforeEvaluatedBuildOperationType.Details
        details.projectPath == ":project1"
        details.buildPath == ":"

        assertOpFailureOrNot(op, NotifyProjectBeforeEvaluatedBuildOperationType.Result, failureCause)
    }

    static void assertAfterEvaluateOp(TestBuildOperationExecutor.Log.Record op, Throwable failureCause = null) {
        assert op.descriptor.name == 'Notify afterEvaluate listeners of :project1'
        assert op.descriptor.displayName == 'Notify afterEvaluate listeners of :project1'

        def details = op.descriptor.details as NotifyProjectAfterEvaluatedBuildOperationType.Details
        details.projectPath == ":project1"
        details.buildPath == ":"

        assertOpFailureOrNot(op, NotifyProjectAfterEvaluatedBuildOperationType.Result, failureCause)
    }

    private static void assertOpFailureOrNot(TestBuildOperationExecutor.Log.Record op, Class<?> resultType, Throwable failureCause) {
        if (failureCause) {
            assert op.result == null
            assert op.failure instanceof ProjectConfigurationException
            assert op.failure.message == "A problem occurred configuring <project>."
            assert op.failure.cause.is(failureCause)
        } else {
            assert resultType.isInstance(op.result)
            assert op.failure == null
        }
    }

    List<TestBuildOperationExecutor.Log.Record> getOperations() {
        buildOperationExecutor.log.records.toList()
    }


}
