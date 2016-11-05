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
import spock.lang.Unroll

class ExecutionTimeTaskConfigurationIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    def "fails when task is configured using #config during execution time"() {
        buildFile.text = """
            def anAction = {} as Action

            task broken {
                doLast {
                    $config
                }
            }

            task broken2 << {
                $config
            }

            task broken3 << { }

            task broken4 {
                dependsOn broken3
                doLast {
                    broken3.configure { $config }
                }
            }
        """

        when:
        executer.withArgument("--continue")
        for (int i = 0; i < expectedDeprecationWarnings; i++) {
            executer.expectDeprecationWarning()
        }
        fails("broken", "broken2", "broken4")

        then:
        failure.assertHasCause("Cannot call ${description} on task ':broken' after task has started execution.")
        failure.assertHasCause("Cannot call ${description} on task ':broken2' after task has started execution. Check the configuration of task ':broken2' as you may have misused '<<' at task declaration.")
        failure.assertHasCause("Cannot call ${description} on task ':broken3' after task has started execution.")

        where:
        config                                                      | description                              | expectedDeprecationWarnings
        "doFirst(anAction)"                                         | "Task.doFirst(Action)"                   | 2
        "doFirst({})"                                               | "Task.doFirst(Closure)"                  | 2
        "doLast(anAction)"                                          | "Task.doLast(Action)"                    | 2
        "doLast({})"                                                | "Task.doLast(Closure)"                   | 2
        "actions.add(anAction)"                                     | "Task.getActions().add()"                | 2
        "actions.addAll([anAction])"                                | "Task.getActions().addAll()"             | 2
        "actions.set(0, anAction)"                                  | "Task.getActions().set(int, Object)"     | 2
        "actions.removeAll(actions)"                                | "Task.getActions().removeAll()"          | 2
        "actions.remove(actions[0])"                                | "Task.getActions().remove()"             | 2
        "actions.clear()"                                           | "Task.getActions().clear()"              | 2
        "def iter = actions.iterator(); iter.next(); iter.remove()" | "Task.getActions().remove()"             | 2
        "actions = []"                                              | "Task.setActions(List<Action>)"          | 2
        "deleteAllActions()"                                        | "Task.deleteAllActions()"                | 2
        "onlyIf { }"                                                | "Task.onlyIf(Closure)"                   | 2
        "onlyIf({ } as Spec)"                                       | "Task.onlyIf(Spec)"                      | 2
        "setOnlyIf({ })"                                            | "Task.setOnlyIf(Closure)"                | 2
        "onlyIf = ({ } as Spec)"                                    | "Task.setOnlyIf(Spec)"                   | 2
        "enabled = false"                                           | "Task.setEnabled(boolean)"               | 2
        "dependsOn 'a', 'b'"                                        | "Task.dependsOn(Object...)"              | 2
        "dependsOn = ['a', 'b']"                                    | "Task.setDependsOn(Iterable)"            | 2
        "mustRunAfter 'a', 'b'"                                     | "Task.mustRunAfter(Object...)"           | 2
        "mustRunAfter = ['a', 'b']"                                 | "Task.setMustRunAfter(Iterable)"         | 2
        "finalizedBy 'a', 'b'"                                      | "Task.finalizedBy(Object...)"            | 2
        "finalizedBy = ['a', 'b']"                                  | "Task.setFinalizedBy(Iterable)"          | 2
        "inputs.file('a')"                                          | "TaskInputs.file(Object)"                | 2
        "inputs.files('a')"                                         | "TaskInputs.files(Object...)"            | 2
        "inputs.dir('a')"                                           | "TaskInputs.dir(Object)"                 | 2
        "inputs.property('key', 'value')"                           | "TaskInputs.property(String, Object)"    | 2
        "inputs.properties([key: 'value'])"                         | "TaskInputs.properties(Map)"             | 2
        "inputs.source('a')"                                        | "TaskInputs.source(Object)"              | 5
        "inputs.sourceDir('a')"                                     | "TaskInputs.sourceDir(Object)"           | 5
        "outputs.upToDateWhen { }"                                  | "TaskOutputs.upToDateWhen(Closure)"      | 2
        "outputs.upToDateWhen({ } as Spec)"                         | "TaskOutputs.upToDateWhen(Spec)"         | 2
        "outputs.file('a')"                                         | "TaskOutputs.file(Object)"               | 2
        "outputs.files('a')"                                        | "TaskOutputs.files(Object...)"           | 2
        "outputs.namedFiles(['prop':'a'])"                          | "TaskOutputs.namedFiles(Map)"            | 2
        "outputs.namedFiles({ ['prop':'a'] })"                      | "TaskOutputs.namedFiles(Callable)"       | 2
        "outputs.namedDirectories(['prop':'a'])"                    | "TaskOutputs.namedDirectories(Map)"      | 2
        "outputs.namedDirectories({ ['prop':'a'] })"                | "TaskOutputs.namedDirectories(Callable)" | 2
        "outputs.dir('a')"                                          | "TaskOutputs.dir(Object)"                | 2
    }
}
