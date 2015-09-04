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

package org.gradle.execution.taskgraph

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class RuleBasedTaskActionsIntegrationTest extends AbstractIntegrationSpec implements WithRuleBasedTasks {

    @NotYetImplemented
    def "actions are applied to a rule-source task using all task action constructs"() {
        given:
        buildFile << """
        class OverruleTask extends EchoTask {}
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("actionMan", EchoTask) {
                    text = 'This is your commander speaking'
                }
                tasks.create("climbTask", ClimbTask) {}
                tasks.create("jumpTask", JumpTask) {}
                tasks.create("overruleTask", OverruleTask) {}
            }
        }
        apply type: Rules
        tasks.withType(OverruleTask) { it.text = "actionWoman I'm the real commander" }
        tasks.withType(ClimbTask).all { it.steps = 14 }
        tasks.matching { it.name.contains('jump') }.all { it.height = 7 }


        //It should be possible to reference the tasks without having to do tasks.realize()
        assert overruleTask.text == "actionWoman I'm the real commander"
        assert jumpTask.height == 7
        assert climbTask.steps == 14
        """

        when:
        succeeds('actionMan')

        then:
        output.contains("actionMan This is your commander speaking")
    }

    @NotYetImplemented
    def "rule sources can have a task with some action applied to it as a rule subject"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}
        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) {}
            }

            @Mutate
            void plusOne(@Path("tasks.climbTask") ClimbTask climbTask){
                climbTask.steps += 1
            }
        }
        apply type: Rules
        tasks.withType(ClimbTask).all { it.steps = 2 }

        //It should be possible to reference the tasks without having to do tasks.realize()
        assert climbTask.steps == 3
        """

        expect:
        succeeds('help')
    }
}
