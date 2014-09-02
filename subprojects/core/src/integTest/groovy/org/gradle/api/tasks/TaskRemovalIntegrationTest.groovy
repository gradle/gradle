/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.api.Task
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.model.internal.report.unbound.UnboundRule
import org.gradle.model.internal.report.unbound.UnboundRuleInput
import org.hamcrest.Matchers
import spock.lang.Unroll

import static org.gradle.model.report.unbound.UnboundRulesReportMatchers.unbound

class TaskRemovalIntegrationTest extends AbstractIntegrationSpec {

    def "can remove task"() {
        given:
        buildScript """
            task foo {}
            tasks.remove(foo)
            task foo {}
            tasks.remove(foo)
        """

        when:
        fails "foo"

        then:
        failure.assertThatDescription(Matchers.startsWith("Task 'foo' not found in root project"))
    }

    def "can remove task in after evaluate"() {
        given:
        buildScript """
            task foo {}
            afterEvaluate {
                tasks.remove(foo)
            }
        """

        when:
        fails "foo"

        then:
        failure.assertThatDescription(Matchers.startsWith("Task 'foo' not found in root project"))
    }

    @Unroll
    def "cant remove task in after evaluate if task is used by a #annotationClass"() {
        given:
        buildScript """
            import org.gradle.model.*

            task foo {}
            task bar {}

            afterEvaluate {
                tasks.remove(foo)
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {

                }

                @RuleSource
                static class Rules {
                    @$annotationClass
                    void linkFooToBar(@Path("tasks.bar") Task bar, @Path("tasks.foo") Task foo) {
                       // do nothing
                    }
                }
            }

            apply plugin: MyPlugin
        """

        when:
        fails "foo"

        then:
        failure.assertThatCause(unbound(
                UnboundRule.descriptor('MyPlugin$Rules#linkFooToBar(org.gradle.api.Task, org.gradle.api.Task)')
                        .mutableInput(UnboundRuleInput.type(Task).bound().path("tasks.bar"))
                        .immutableInput(UnboundRuleInput.type(Task).path("tasks.foo").suggestions("tasks.bar"))
        ))

        where:
        annotationClass << ["Mutate", "Finalize"]
    }
}
