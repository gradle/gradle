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

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.function.Supplier

class StateTransitionControllerTest extends ConcurrentSpec {
    enum TestState implements StateTransitionController.State {
        A, B, C
    }

    def "runs action for transition when in from state"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)

        when:
        controller.transition(TestState.A, TestState.B, action)

        then:
        1 * action.run()
        0 * _
    }

    def "runs action and returns result for transition when in from state"() {
        def action = Mock(Supplier)
        def controller = new StateTransitionController(TestState.A)

        when:
        def result = controller.transition(TestState.A, TestState.B, action)

        then:
        result == "result"
        1 * action.get() >> "result"
        0 * _
    }

    def "fails transition when already in to state"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)
        controller.transition(TestState.A, TestState.B, action)

        when:
        controller.transition(TestState.A, TestState.B, action)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Can only transition to state B from state A however currently in state B."

        and:
        0 * _
    }

    def "fails transition when previous transition has failed"() {
        def action = Mock(Runnable)
        def failure = new RuntimeException()
        def controller = new StateTransitionController(TestState.A)

        when:
        controller.transition(TestState.A, TestState.B) { throw failure }

        then:
        def e = thrown(RuntimeException)
        e == failure

        when:
        controller.transition(TestState.B, TestState.C, action)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot use this object as a previous transition failed."

        and:
        0 * _
    }

    def "fails transition when current thread is already transitioning"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)

        when:
        controller.transition(TestState.A, TestState.B, action)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot transition to state B as already transitioning to this state."

        1 * action.run() >> {
            controller.transition(TestState.A, TestState.B, {})
        }
        0 * _
    }

    def "cannot attempt to transition while another thread is performing transition"() {
        def controller = new StateTransitionController(TestState.A)

        when:
        async {
            start {
                controller.transition(TestState.A, TestState.B) {
                    instant.transitioning
                    thread.block()
                }
            }
            start {
                thread.blockUntil.transitioning
                controller.transition(TestState.A, TestState.B) {
                }
            }

        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Another thread is currently transitioning state from A to B."
    }

    def "runs action when not in forbidden state"() {
        def action = Mock(Supplier)
        def controller = new StateTransitionController(TestState.A)
        controller.transition(TestState.A, TestState.B) {}

        when:
        controller.notInStateIgnoreOtherThreads(TestState.A, action)

        then:
        1 * action.get() >> "result"
        0 * _
    }

    def "fails when in forbidden state"() {
        def action = Mock(Supplier)
        def controller = new StateTransitionController(TestState.A)
        controller.transition(TestState.A, TestState.B) {}

        when:
        controller.notInStateIgnoreOtherThreads(TestState.B, action)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Should not be in state B."

        0 * _
    }

    def "collects action failure when not in forbidden state"() {
        def failure = new RuntimeException()
        def action = Mock(Supplier)
        def controller = new StateTransitionController(TestState.A)
        controller.transition(TestState.A, TestState.B) {}

        when:
        controller.notInStateIgnoreOtherThreads(TestState.A, action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action.get() >> { throw failure }
        0 * _

        when:
        controller.notInStateIgnoreOtherThreads(TestState.B, action)

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        and:
        0 * _
    }

    def "produces value when in expected state"() {
        def action = Mock(Supplier)
        def controller = new StateTransitionController(TestState.A)
        controller.transition(TestState.A, TestState.B) {}

        when:
        def result = controller.inState(TestState.B, action)

        then:
        result == "result"
        1 * action.get() >> "result"
        0 * _
    }

    def "runs action when in expected state"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)
        controller.transition(TestState.A, TestState.B) {}

        when:
        controller.inState(TestState.B, action)

        then:
        1 * action.run()
        0 * _
    }

    def "fails when not in expected state"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)
        controller.transition(TestState.A, TestState.B) {}

        when:
        controller.inState(TestState.C, action)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Expected to be in state C but is in state B."

        0 * _
    }

    def "collects action failure when in expected state"() {
        def failure = new RuntimeException()
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)
        controller.transition(TestState.A, TestState.B) {}

        when:
        controller.inState(TestState.B, action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action.run() >> { throw failure }
        0 * _

        when:
        controller.inState(TestState.B, action)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot use this object as a previous transition failed."

        and:
        0 * _
    }

    def "cannot attempt to run action while another thread is performing transition"() {
        def controller = new StateTransitionController(TestState.A)

        when:
        async {
            start {
                controller.transition(TestState.A, TestState.B) {
                    instant.transitioning
                    thread.block()
                }
            }
            start {
                thread.blockUntil.transitioning
                controller.inState(TestState.A) {
                }
            }

        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Another thread is currently transitioning state from A to B."
    }

    def "runs action for conditional transition when in from state"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)

        when:
        controller.transitionIfNotPreviously(TestState.A, TestState.B, action)

        then:
        1 * action.run()
        0 * _
    }

    def "does not run action for conditional transition when already in to state"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)
        controller.transitionIfNotPreviously(TestState.A, TestState.B) {}

        when:
        controller.transitionIfNotPreviously(TestState.A, TestState.B, action)

        then:
        0 * _
    }

    def "does not run action for conditional transition when has already transitioned from to state"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)
        controller.transitionIfNotPreviously(TestState.A, TestState.B) {}
        controller.transitionIfNotPreviously(TestState.B, TestState.C) {}

        when:
        controller.transitionIfNotPreviously(TestState.A, TestState.B, action)

        then:
        0 * _
    }

    def "rethrows failure for conditional transition when previous transition has failed"() {
        def action = Mock(Runnable)
        def failure = new RuntimeException()
        def controller = new StateTransitionController(TestState.A)

        when:
        controller.transitionIfNotPreviously(TestState.A, TestState.B) { throw failure }

        then:
        def e = thrown(RuntimeException)
        e == failure

        when:
        controller.transitionIfNotPreviously(TestState.B, TestState.C, action)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot use this object as a previous transition failed."

        and:
        0 * _
    }

    def "action cannot attempt to do conditional transition while not in from state"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)

        when:
        controller.transitionIfNotPreviously(TestState.B, TestState.C, action)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Can only transition to state C from state B however currently in state A."
    }

    def "action cannot attempt to do conditional transition while already transitioning"() {
        def action = Mock(Runnable)
        def controller = new StateTransitionController(TestState.A)

        when:
        controller.transitionIfNotPreviously(TestState.A, TestState.B, action)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot transition to state B as already transitioning to this state."

        1 * action.run() >> {
            controller.transitionIfNotPreviously(TestState.A, TestState.B, {})
        }
        0 * _
    }

    def "cannot attempt to conditional transition while another thread is performing transition"() {
        def controller = new StateTransitionController(TestState.A)

        when:
        async {
            start {
                controller.transitionIfNotPreviously(TestState.A, TestState.B) {
                    instant.transitioning
                    thread.block()
                }
            }
            start {
                thread.blockUntil.transitioning
                controller.transitionIfNotPreviously(TestState.A, TestState.B) {
                }
            }

        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Another thread is currently transitioning state from A to B."
    }
}
