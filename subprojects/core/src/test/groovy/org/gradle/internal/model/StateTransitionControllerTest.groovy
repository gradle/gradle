/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.model

import org.gradle.internal.Describables
import org.gradle.internal.Factory
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.function.Function
import java.util.function.Supplier

class StateTransitionControllerTest extends ConcurrentSpec {
    enum TestState implements StateTransitionController.State {
        A, B, C
    }

    final def workerLeaseService = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), new DefaultParallelismConfiguration(true, 20))

    def setup() {
        workerLeaseService.startProjectExecution(true)
    }

    StateTransitionController<TestState> controller(TestState initialState) {
        return new StateTransitionController<TestState>(Describables.of("<state>"), initialState, workerLeaseService.newResource())
    }

    def <T> T asWorker(Factory<T> action) {
        return workerLeaseService.runAsWorkerThread(action)
    }

    def "runs action for transition when in from state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        1 * action.run()
        0 * _
    }

    def "runs action and returns result for transition when in from state"() {
        def action = Mock(Supplier)
        def controller = controller(TestState.A)

        when:
        def result = asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        result == "result"
        1 * action.get() >> "result"
        0 * _
    }

    def "fails transition when already in to state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        when:
        asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Can only transition <state> to state B from state A however it is currently in state B."

        and:
        0 * _
    }

    def "fails transition when previous transition has failed"() {
        def action = Mock(Runnable)
        def failure = new RuntimeException()
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transition(TestState.A, TestState.B) { throw failure }
        }

        then:
        def e = thrown(RuntimeException)
        e == failure

        when:
        asWorker {
            controller.transition(TestState.B, TestState.C, action)
        }

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        and:
        0 * _
    }

    def "fails transition when current thread is already transitioning"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot transition <state> to state B as already transitioning to this state."

        1 * action.run() >> {
            controller.transition(TestState.A, TestState.B, {})
        }
        0 * _
    }

    def "blocks transition while another thread is performing transition"() {
        def controller = controller(TestState.A)

        when:
        async {
            start {
                asWorker {
                    controller.transition(TestState.A, TestState.B) {
                        instant.first
                        thread.block()
                    }
                }
            }
            start {
                thread.blockUntil.first
                asWorker {
                    controller.transition(TestState.B, TestState.C) {
                        instant.second
                    }
                }
            }
        }

        then:
        instant.second > instant.first
    }

    def "runs action when not in forbidden state"() {
        def action = Mock(Supplier)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.notInState(TestState.A, action)
        }

        then:
        1 * action.get() >> "result"
        0 * _
    }

    def "fails when in forbidden state"() {
        def action = Mock(Supplier)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.notInState(TestState.B, action)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "<state> should not be in state B."

        0 * _
    }

    def "collects action failure when not in forbidden state"() {
        def failure = new RuntimeException()
        def action = Mock(Supplier)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.notInState(TestState.A, action)
        }

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action.get() >> { throw failure }
        0 * _

        when:
        asWorker {
            controller.notInState(TestState.B, action)
        }

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        and:
        0 * _
    }

    def "runs action when not in forbidden state and ignoring other threads"() {
        def action = Mock(Supplier)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.notInStateIgnoreOtherThreads(TestState.A, action)
        }

        then:
        1 * action.get() >> "result"
        0 * _
    }

    def "fails when in forbidden state and ignoring other threads"() {
        def action = Mock(Supplier)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.notInStateIgnoreOtherThreads(TestState.B, action)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "<state> should not be in state B."

        0 * _
    }

    def "collects action failure when not in forbidden state and ignoring other threads"() {
        def failure = new RuntimeException()
        def action = Mock(Supplier)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.notInStateIgnoreOtherThreads(TestState.A, action)
        }

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action.get() >> { throw failure }
        0 * _

        when:
        asWorker {
            controller.notInStateIgnoreOtherThreads(TestState.B, action)
        }

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        and:
        0 * _
    }

    def "produces value when in expected state"() {
        def action = Mock(Supplier)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        def result = asWorker {
            controller.inState(TestState.B, action)
        }

        then:
        result == "result"
        1 * action.get() >> "result"
        0 * _
    }

    def "runs action when in expected state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.inState(TestState.B, action)
        }

        then:
        1 * action.run()
        0 * _
    }

    def "fails to run action when not in expected state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.inState(TestState.C, action)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Expected <state> to be in state C but is in state B."

        0 * _
    }

    def "collects action failure when in expected state"() {
        def failure = new RuntimeException()
        def action = Mock(Runnable)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.inState(TestState.B, action)
        }

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action.run() >> { throw failure }
        0 * _

        when:
        asWorker {
            controller.inState(TestState.B, action)
        }

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        and:
        0 * _
    }

    def "blocks attempting to run action while another thread is performing transition"() {
        def controller = controller(TestState.A)

        when:
        async {
            start {
                asWorker {
                    controller.transition(TestState.A, TestState.B) {
                        instant.transitioning
                        thread.block()
                    }
                }
            }
            start {
                thread.blockUntil.transitioning
                asWorker {
                    controller.inState(TestState.A) {
                    }
                }
            }

        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Expected <state> to be in state A but is in state B."
    }

    def "can assert is in expected state or later state when in expected state"() {
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        expect:
        controller.assertInStateOrLater(TestState.B)
    }

    def "can assert is in expected state or later state when in later state"() {
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
            controller.transition(TestState.B, TestState.C) {}
        }

        expect:
        controller.assertInStateOrLater(TestState.B)
    }

    def "fails when not in expected state or later"() {
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        controller.assertInStateOrLater(TestState.C)

        then:
        def e = thrown(IllegalStateException)
        e.message == "<state> should be in state C or later."
    }

    def "maybeTransition() runs action for transition when in from state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.maybeTransition(TestState.A, TestState.B, action)
        }

        then:
        1 * action.run()
        0 * _
    }

    def "maybeTransition() does not run action for transition when in to state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.maybeTransition(TestState.A, TestState.B, action)
        }

        then:
        0 * _
    }

    def "maybeTransition() fails when not in from or to state"() {
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.C) {}
        }

        when:
        asWorker {
            controller.maybeTransition(TestState.A, TestState.B) {}
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Can only transition <state> to state B from state A however it is currently in state C."
    }

    def "maybeTransition() fails when current thread already transitioning to target state"() {
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transition(TestState.A, TestState.B) {
                controller.maybeTransition(TestState.A, TestState.B) {}
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Expected <state> to be in state B but is in state A and transitioning to B."
    }

    def "maybeTransitionIfNotCurrentlyTransitioning() runs action for transition when in from state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.maybeTransitionIfNotCurrentlyTransitioning(TestState.A, TestState.B, action)
        }

        then:
        1 * action.run()
        0 * _
    }

    def "maybeTransitionIfNotCurrentlyTransitioning() does not run action for transition when in to state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.maybeTransitionIfNotCurrentlyTransitioning(TestState.A, TestState.B, action)
        }

        then:
        0 * _
    }

    def "maybeTransitionIfNotCurrentlyTransitioning() does not run action when transitioning to target state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transition(TestState.A, TestState.B) {
                controller.maybeTransitionIfNotCurrentlyTransitioning(TestState.A, TestState.B, action)
            }
        }

        then:
        0 * _
    }

    def "transitionIfNotPreviously() runs action for transition when in from state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transitionIfNotPreviously(TestState.A, TestState.B, action)
        }

        then:
        1 * action.run()
        0 * _
    }

    def "transitionIfNotPreviously() does not run action for transition when already in to state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)
        asWorker {
            controller.transitionIfNotPreviously(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.transitionIfNotPreviously(TestState.A, TestState.B, action)
        }

        then:
        0 * _
    }

    def "transitionIfNotPreviously() does not run action for transition when has already transitioned from to state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)
        asWorker {
            controller.transitionIfNotPreviously(TestState.A, TestState.B) {}
            controller.transitionIfNotPreviously(TestState.B, TestState.C) {}
        }

        when:
        asWorker {
            controller.transitionIfNotPreviously(TestState.A, TestState.B, action)
        }

        then:
        0 * _
    }

    def "transitionIfNotPreviously() rethrows failure for transition when previous transition has failed"() {
        def action = Mock(Runnable)
        def failure = new RuntimeException()
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transitionIfNotPreviously(TestState.A, TestState.B) { throw failure }
        }

        then:
        def e = thrown(RuntimeException)
        e == failure

        when:
        asWorker {
            controller.transitionIfNotPreviously(TestState.B, TestState.C, action)
        }

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        and:
        0 * _
    }

    def "transitionIfNotPreviously() fails when not in from state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transitionIfNotPreviously(TestState.B, TestState.C, action)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Can only transition <state> to state C from state B however it is currently in state A."
    }

    def "transitionIfNotPreviously() fails when already transitioning to state"() {
        def action = Mock(Runnable)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transitionIfNotPreviously(TestState.A, TestState.B, action)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Expected <state> to be in state B or later but is in state A and transitioning to B."

        1 * action.run() >> {
            controller.transitionIfNotPreviously(TestState.A, TestState.B, {})
        }
        0 * _
    }

    def "transitionIfNotPreviously() blocks while another thread is performing transition"() {
        def controller = controller(TestState.A)

        when:
        async {
            start {
                asWorker {
                    controller.transitionIfNotPreviously(TestState.A, TestState.B) {
                        instant.transitioning
                        thread.block()
                    }
                }
            }
            start {
                thread.blockUntil.transitioning
                asWorker {
                    controller.transitionIfNotPreviously(TestState.A, TestState.B) {
                    }
                }
            }
        }

        then:
        noExceptionThrown()
    }

    def "can transition to new state and take previous failure as input when nothing has failed"() {
        def action = Mock(Function)
        def controller = controller(TestState.A)

        when:
        def result = asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        1 * action.apply(_) >> { ExecutionResult r ->
            assert r.failures.empty
            ExecutionResult.succeeded()
        }
        0 * action._
        result.failures.empty
    }

    def "can transition to new state and take previous failure as input"() {
        def action = Mock(Function)
        def controller = controller(TestState.A)

        given:
        asWorker {
            makeBroken(controller)
        }

        when:
        def result = asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        1 * action.apply(_) >> { ExecutionResult r ->
            assert r.failures.size() == 1
            ExecutionResult.succeeded()
        }
        0 * action._
        result.failures.empty
    }

    def "retains failure in action that takes previous failure as input"() {
        def action = Mock(Function)
        def failure = new RuntimeException()
        def controller = controller(TestState.A)

        when:
        def result = asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        1 * action.apply(_) >> { ExecutionResult r ->
            throw failure
        }
        0 * action._
        result.failures == [failure]

        when:
        asWorker {
            controller.transition(TestState.B, TestState.A) {}
        }

        then:
        def e = thrown(RuntimeException)
        e == failure
    }

    def "transition that takes previous failure as input fails when in unexpected state"() {
        def action = Mock(Function)
        def controller = controller(TestState.A)

        when:
        asWorker {
            controller.transition(TestState.B, TestState.C, action)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Can only transition <state> to state C from state B however it is currently in state A.'
    }

    def "can reset state to initial state"() {
        def action = Mock(Runnable)
        def resetAction = Mock(Runnable)
        def controller = controller(TestState.A)

        given:
        asWorker {
            controller.transition(TestState.A, TestState.B) {}
        }

        when:
        asWorker {
            controller.restart(TestState.B, TestState.A, resetAction)
        }

        then:
        1 * resetAction.run()
        0 * _

        when:
        asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        1 * action.run()
        0 * _
    }

    def "can reset state to initial state and discard collected failure"() {
        def action = Mock(Runnable)
        def resetAction = Mock(Runnable)
        def controller = controller(TestState.A)

        given:
        asWorker {
            makeBroken(controller)
        }

        when:
        asWorker {
            controller.restart(TestState.B, TestState.A, resetAction)
        }

        then:
        1 * resetAction.run()
        0 * _

        when:
        asWorker {
            controller.transition(TestState.A, TestState.B, action)
        }

        then:
        1 * action.run()
        0 * _
    }

    def "reset state fails when not in expected from state"() {
        def resetAction = Mock(Runnable)
        def controller = controller(TestState.A)

        given:
        asWorker {
            controller.transition(TestState.A, TestState.C) {}
        }

        when:
        asWorker {
            controller.restart(TestState.B, TestState.A, resetAction)
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Can only transition <state> to state A from state B however it is currently in state C.'
    }

    private makeBroken(StateTransitionController<TestState> controller) {
        try {
            controller.transition(TestState.A, TestState.B) {
                throw new RuntimeException("broken")
            }
        } catch (RuntimeException e) {
            // ignore
        }
    }
}
