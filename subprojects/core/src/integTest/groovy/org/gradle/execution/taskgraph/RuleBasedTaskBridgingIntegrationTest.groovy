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
import org.gradle.util.TextUtil

class RuleBasedTaskBridgingIntegrationTest extends AbstractIntegrationSpec implements WithRuleBasedTasks {

    def "a non-rule-source task can depend on a rule-source task"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask << { }
        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':climbTask'])
    }

    def "a non-rule-source task can depend on one or more task of types created via both rule sources and old world container"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task oldClimber(type: ClimbTask) { }
        task customTask << { }

        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':customTask', ':climbTask'])
    }

    def "can depend on a rule-source task in a project which has already evaluated"() {
        given:
        settingsFile << 'include "sub1", "sub2"'
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }

        project("sub1") {
            apply type: Rules
        }

        project("sub2") {
            evaluationDependsOn ':sub1'

            task customTask  {
                dependsOn project(":sub1").tasks.withType(ClimbTask)
            }
        }
        """

        when:
        succeeds('sub2:customTask')

        then:
        result.executedTasks.containsAll([':sub2:customTask', ':sub1:climbTask'])
    }

    def "can depend on a rule-source task after a project has been evaluated"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask << { }

        afterEvaluate {
            customTask.dependsOn tasks.withType(ClimbTask)
        }
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':climbTask'])
    }

    def "a build failure occurs when depending on a rule task with failing configuration"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) {
                    throw new GradleException("Bang")
                }
            }
        }
        apply type: Rules

        task customTask << { }

        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        fails('customTask')

        then:
        failure.assertHasCause('Bang')
        failure.assertHasDescription('Exception thrown while executing model rule: Rules#addTasks > create(climbTask)')
    }

    def "can not depend on a general Task"() {
        given:
        buildFile << """
        task customTask << { }
        customTask.dependsOn tasks.withType(Task)
        """

        when:
        fails('customTask')

        then:
        failure.assertHasDescription(TextUtil.normaliseLineSeparators("""Circular dependency between the following tasks:
:customTask
\\--- :customTask (*)"""))
    }

    def "a non-rule-source task can depend on a rule-source task through another task collection"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask << { }
def t = tasks.withType(Task).withType(ClimbTask)
println t.getClass()
        customTask.dependsOn t
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':climbTask'])
    }

    def "a non-rule-source task can depend on a rule-source task with matching criteria"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask << { }
        customTask.dependsOn tasks.withType(ClimbTask).matching { true }
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':climbTask'])
    }

    def "a non-rule-source task can not depend on both realizable and default task collections"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task foo << { }
        task customTask << { }
        customTask.dependsOn tasks.withType(ClimbTask) + [tasks.foo]
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':foo'])
    }

    @NotYetImplemented
    def "a non-rule-source task can depend on combined task collections"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
                tasks.create("jumpTask", JumpTask) { }
            }
        }
        apply type: Rules

        task customTask << { }
        customTask.dependsOn tasks.withType(ClimbTask) + tasks.withType(JumpTask)
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':climbTask', ':jumpTask'])
    }

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

    def "only tasks of specified type are realized"() {
        when:
        buildScript """
            ${ruleBasedTasks()}

            model {
              tasks {
                create("climbTask", ClimbTask)
                create("jumpTask", JumpTask)
              }
            }

            task customTask {
              dependsOn tasks.withType(ClimbTask)
              doLast {
                // This is somewhat fragile and may break when we handle realizing better
                println "jumpTask exists: " + !tasks.withType(JumpTask).empty
                println "climbTask exists: " + !tasks.withType(ClimbTask).empty
              }
            }
        """

        and:
        run "customTask"

        then:
        outputContains "jumpTask exists: false"
        outputContains "climbTask exists: true"
    }
}
