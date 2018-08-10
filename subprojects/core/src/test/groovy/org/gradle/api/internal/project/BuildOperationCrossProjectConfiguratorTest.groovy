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
    def project = Mock(Project)
    def calledAction = false
    def actionCallingDisallowedMethod = new Action<Project>() {
        @Override
        void execute(Project project) {
            calledAction = true
            disallowedMethod()
        }
    }

    def "does not throw exception when calling a disallowed method when allowed"() {
        given:
        def action = service.withCrossProjectConfigurationEnabled(actionCallingDisallowedMethod)

        when:
        action.execute(project)

        then:
        noExceptionThrown()
        calledAction
    }

    def "throws IllegalStateException when calling a disallowed method when disallowed"() {
        given:
        def action = service.withCrossProjectConfigurationDisabled(actionCallingDisallowedMethod)

        when:
        action.execute(project)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "someProtectedMethod() on Mock for type 'Project' cannot be executed in the current context."
    }

    def "doesn't throw exception when calling disallowed method when allowed"() {
        when:
        disallowedMethod()

        then:
        noExceptionThrown()
    }

    def "call to withCrossProjectConfigurationDisabled does not disable disallow check"() {
        def action = service.withCrossProjectConfigurationDisabled(new Action<Project>() {
            @Override
            void execute(Project project) {
                service.withCrossProjectConfigurationDisabled(Actions.doNothing()).execute(project)
                disallowedMethod()
            }
        })
        when:
        action.execute(project)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "someProtectedMethod() on Mock for type 'Project' cannot be executed in the current context."
    }

    def "call to withCrossProjectConfigurationEnabled does not disable disallow check"() {
        def action = service.withCrossProjectConfigurationDisabled(new Action<Project>() {
            @Override
            void execute(Project project) {
                service.withCrossProjectConfigurationEnabled(Actions.doNothing()).execute(project)
                disallowedMethod()
            }
        })
        when:
        action.execute(project)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "someProtectedMethod() on Mock for type 'Project' cannot be executed in the current context."
    }

    def "call to withCrossProjectConfigurationDisabled does enable disallow check outside scope"() {
        def action = service.withCrossProjectConfigurationEnabled(new Action<Project>() {
            @Override
            void execute(Project project) {
                service.withCrossProjectConfigurationDisabled(Actions.doNothing()).execute(project)
                disallowedMethod()
            }
        })
        when:
        action.execute(project)

        then:
        noExceptionThrown()
    }

    def "doesn't protect across thread boundaries"() {
        given:
        def action = service.withCrossProjectConfigurationDisabled(new Action<Project>() {
            @Override
            void execute(Project project) {
                def thread = new Thread(new Runnable() {
                    @Override
                    void run() {
                        actionCallingDisallowedMethod.execute(project)
                    }
                })
                thread.start()
                thread.join()
            }
        })

        when:
        action.execute(project)

        then:
        noExceptionThrown()
        calledAction
    }

    private void disallowedMethod() {
        service.assertCrossProjectConfigurationAllowed("someProtectedMethod()", Mock(Project))
    }
}
