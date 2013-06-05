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

import org.gradle.api.ProjectConfigurationException
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateInternal
import spock.lang.Specification

public class LifecycleProjectEvaluatorTest extends Specification {
    private project = Mock(ProjectInternal)
    private listener = Mock(ProjectEvaluationListener)
    private delegate = Mock(ProjectEvaluator)
    private state = Mock(ProjectStateInternal)
    private evaluator = new LifecycleProjectEvaluator(delegate)

    void setup() {
        project.getProjectEvaluationBroadcaster() >> listener
        project.toString() >> "project1"
    }

    void "nothing happens if project was already configured"() {
        state.executed >> true

        when:
        evaluator.evaluate(project, state)

        then:
        0 * delegate._
    }

    void "nothing happens if project is being configured now"() {
        state.executing >> true

        when:
        evaluator.evaluate(project, state)

        then:
        0 * delegate._
    }
    
    void "evaluates the project firing all necessary listeners and updating the state"() {
        when:
        evaluator.evaluate(project, state)

        then:
        1 * listener.beforeEvaluate(project)
        1 * state.setExecuting(true)

        then:
        1 * delegate.evaluate(project, state)

        then:
        1 * state.setExecuting(false)
        1 * state.executed()
        1 * listener.afterEvaluate(project, state)
    }

    void "notifies listeners and updates state on evaluation failure"() {
        def failure = new RuntimeException()

        when:
        evaluator.evaluate(project, state)

        then:
        delegate.evaluate(project, state) >> { throw failure }

        and:
        1 * state.executed({
            assertIsConfigurationFailure(it, failure)
        })
        1 * state.setExecuting(false)
        1 * listener.afterEvaluate(project, state)
    }

    void "updates state and does not delegate when beforeEvaluate action fails"() {
        def failure = new RuntimeException()

        when:
        evaluator.evaluate(project, state)

        then:
        1 * listener.beforeEvaluate(project) >> { throw failure }
        1 * state.executed({
            assertIsConfigurationFailure(it, failure)
        })
        0 * delegate._
        0 * listener._
    }

    void "updates state when afterEvaluate action fails"() {
        def failure = new RuntimeException()

        when:
        evaluator.evaluate(project, state)

        then:
        delegate.evaluate(project, state)

        and:
        1 * state.setExecuting(false)
        1 * state.executed()

        then:
        1 * listener.afterEvaluate(project, state) >> { throw failure }
        1 * state.executed({
            assertIsConfigurationFailure(it, failure)
        })
    }

    def assertIsConfigurationFailure(def it, def cause) {
        assert it instanceof ProjectConfigurationException
        assert it.message == "A problem occurred configuring project1."
        assert it.cause == cause
        true
    }

    void "notifies listeners and updates state on evaluation failure even if afterEvaluate fails"() {
        def failure = new RuntimeException()

        when:
        evaluator.evaluate(project, state)

        then:
        delegate.evaluate(project, state) >> { throw failure }

        and:
        _ * project.toString() >> "project1"
        1 * state.executed(_)
        1 * state.setExecuting(false)

        then:
        1 * listener.afterEvaluate(project, state) >> { throw new RuntimeException("afterEvaluate")}
        1 * state.hasFailure() >> true
        0 * state.executed(_)
    }
}
