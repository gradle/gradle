/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ExecutionTimeTaskConfigurationIntegrationTest extends AbstractIntegrationSpec {
    def "fails when task is configured using #config during execution time"() {
        buildFile.text = """
            def anAction = {} as Action

            task broken1 {
                doLast {
                    $config
                }
            }

            task broken2 {
                doLast {}
            }

            task broken3 {
                dependsOn broken2
                doLast {
                    broken2.configure { $config }
                }
            }
        """

        when:
        executer.withArgument("--continue")
        fails("broken1", "broken2", "broken3")

        then:
        failure.assertHasCause("Cannot call ${description} on task ':broken1' after task has started execution.")
        failure.assertHasCause("Cannot call ${description} on task ':broken2' after task has started execution.")

        where:
        config                                                      | description
        "doFirst(anAction)"                                         | "Task.doFirst(Action)"
        "doFirst({})"                                               | "Task.doFirst(Closure)"
        "doLast(anAction)"                                          | "Task.doLast(Action)"
        "doLast({})"                                                | "Task.doLast(Closure)"
        "actions.add(anAction)"                                     | "Task.getActions().add()"
        "actions.addAll([anAction])"                                | "Task.getActions().addAll()"
        "actions.set(0, anAction)"                                  | "Task.getActions().set(int, Object)"
        "actions.removeAll(actions)"                                | "Task.getActions().removeAll()"
        "actions.remove(actions[0])"                                | "Task.getActions().remove()"
        "actions.clear()"                                           | "Task.getActions().clear()"
        "def iter = actions.iterator(); iter.next(); iter.remove()" | "Task.getActions().remove()"
        "actions = []"                                              | "Task.setActions(List<Action>)"
        "onlyIf { }"                                                | "Task.onlyIf(Closure)"
        "onlyIf({ } as Spec)"                                       | "Task.onlyIf(Spec)"
        "setOnlyIf({ })"                                            | "Task.setOnlyIf(Closure)"
        "onlyIf = ({ } as Spec)"                                    | "Task.setOnlyIf(Spec)"
        "enabled = false"                                           | "Task.setEnabled(boolean)"
        "dependsOn 'a', 'b'"                                        | "Task.dependsOn(Object...)"
        "dependsOn = ['a', 'b']"                                    | "Task.setDependsOn(Iterable)"
        "mustRunAfter 'a', 'b'"                                     | "Task.mustRunAfter(Object...)"
        "mustRunAfter = ['a', 'b']"                                 | "Task.setMustRunAfter(Iterable)"
        "finalizedBy 'a', 'b'"                                      | "Task.finalizedBy(Object...)"
        "finalizedBy = ['a', 'b']"                                  | "Task.setFinalizedBy(Iterable)"
        "inputs.file('a')"                                          | "TaskInputs.file(Object)"
        "inputs.files('a')"                                         | "TaskInputs.files(Object...)"
        "inputs.dir('a')"                                           | "TaskInputs.dir(Object)"
        "inputs.property('key', 'value')"                           | "TaskInputs.property(String, Object)"
        "inputs.properties([key: 'value'])"                         | "TaskInputs.properties(Map)"
        "outputs.upToDateWhen { }"                                  | "TaskOutputs.upToDateWhen(Closure)"
        "outputs.upToDateWhen({ } as Spec)"                         | "TaskOutputs.upToDateWhen(Spec)"
        "outputs.cacheIf({ } as Spec)"                              | "TaskOutputs.cacheIf(Spec)"
        "outputs.file('a')"                                         | "TaskOutputs.file(Object)"
        "outputs.files('a')"                                        | "TaskOutputs.files(Object...)"
        "outputs.dirs(['prop':'a'])"                                | "TaskOutputs.dirs(Object...)"
        "outputs.dir('a')"                                          | "TaskOutputs.dir(Object)"
    }
}
