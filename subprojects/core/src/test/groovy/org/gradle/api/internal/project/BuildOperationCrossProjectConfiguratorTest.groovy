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

package org.gradle.api.internal.project

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.internal.Actions
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.testing.internal.util.Specification

class BuildOperationCrossProjectConfiguratorTest extends Specification {
    def service = new BuildOperationCrossProjectConfigurator(new TestBuildOperationExecutor())

    def "throws IllegalStateException when calling a disallowed method when disallowed"() {
        given:
        def action = service.withCrossProjectConfigurationDisabled(newActionThatCallsDisallowedMethod())

        when:
        action.execute(new Object())

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "someProtectedMethod() on Mock for type 'Project' cannot be executed in the current context."
    }

    def "doesn't throw exception when calling disallowed method when allowed"() {
        when:
        callsDisallowedMethod(new Object())

        then:
        noExceptionThrown()
    }

    def "doesn't protect across thread boundaries"() {
        given:
        def innerAction = Mock(Action)
        def action = service.withCrossProjectConfigurationDisabled(new Action<Object>() {
            @Override
            void execute(Object o) {
                def thread = new Thread(new Runnable() {
                    @Override
                    void run() {
                        try {
                            callsDisallowedMethod(o, innerAction)
                        } catch (Throwable ex) {
                            assert false : "this should never occur"
                        }
                    }
                })
                thread.start()
                thread.join()
            }
        })

        when:
        action.execute(new Object())

        then:
        noExceptionThrown()
        1 * innerAction.execute(_)
    }

    private Action<Object> newActionThatCallsDisallowedMethod() {
        return new Action<Object>() {
            @Override
            void execute(Object o) {
                callsDisallowedMethod(o)
            }
        }
    }

    private void callsDisallowedMethod(Object o, Action action = Actions.doNothing()) {
        action.execute(o)
        service.assertCrossProjectConfigurationAllowed("someProtectedMethod()", Mock(Project))
    }
}
