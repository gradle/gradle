/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ScopedRuleSourceIntegrationTest extends AbstractIntegrationSpec {

    def "rule source can be applied in scope of a collection builder element"() {
        when:
        buildScript '''
            class MessageTask extends DefaultTask {
                String message = "default"

                @TaskAction
                void printMessages() {
                    println "message: $message"
                }
            }

            class EchoRules extends RuleSource {
                @Mutate
                void mutateEcho(Task echo, String message) {
                    echo.message = message
                }
            }

            class Rules extends RuleSource {
                @Model
                String message() {
                    "foo"
                }

                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    tasks.create("echo", MessageTask)
                    tasks.named("echo", EchoRules)
                }
            }

            apply type: Rules
        '''

        then:
        succeeds "echo"

        and:
        output.contains "message: foo"
    }

    def "scoped rule execution failure yields useful error message"() {
        when:
        buildScript '''
            class ThrowingRule extends RuleSource {
                @Mutate
                void badRule(Task echo) {
                    throw new RuntimeException("I'm broken")
                }
            }

            class Rules extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    tasks.named("taskWithThrowingRuleApplied", ThrowingRule)
                    tasks.create("taskWithThrowingRuleApplied")
                }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: ThrowingRule#badRule")
        failure.assertHasCause("I'm broken")
    }

    def "invalid rule definitions of scoped rules are reported with a message helping to identify the faulty rule"() {
        when:
        buildScript '''
            class InvalidRuleSource extends RuleSource {
                @Mutate
                String invalidRule(Task echo) {
                }
            }

            class Rules extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    tasks.named("taskWithInvalidRuleSourceApplied", InvalidRuleSource)
                    tasks.create("taskWithInvalidRuleSourceApplied")
                }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: Rules#addTasks")
        failure.assertHasCause("InvalidRuleSource#invalidRule is not a valid model rule method")
    }

    def "unbound inputs of scoped rules are reported and their scope is shown"() {
        when:
        buildScript '''
            class UnboundRuleSource extends RuleSource {
                @Mutate
                void unboundRule(String string, Integer integer, @Path("some.inner.path") String withInnerPath) {
                }
            }

            class Rules extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    tasks.named("taskWithUnboundRuleSourceApplied", UnboundRuleSource)
                    tasks.create("taskWithUnboundRuleSourceApplied")
                }
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failureCauseContains '''
  UnboundRuleSource#unboundRule
    subject:
      - <no path> String (parameter 1) [*]
          scope: tasks.taskWithUnboundRuleSourceApplied
    inputs:
      - <no path> Integer (parameter 2) [*]
      - tasks.taskWithUnboundRuleSourceApplied.some.inner.path String (parameter 3) [*]
'''
    }

}
