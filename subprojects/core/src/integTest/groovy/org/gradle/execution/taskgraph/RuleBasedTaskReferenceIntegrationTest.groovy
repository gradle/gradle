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
import spock.lang.Issue

import static org.gradle.util.TextUtil.normaliseFileSeparators

class RuleBasedTaskReferenceIntegrationTest extends AbstractIntegrationSpec implements WithRuleBasedTasks {

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

    def "a non-rule-source task can depend on a rule-source task when referenced via various constructs"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
                tasks.create("jumpTask", JumpTask) { }
                tasks.create("echoTask", EchoTask) { }
            }
        }
        apply type: Rules

        task customClimbTask << { }
        task customEchoTask << { }
        task customJumpTask << { }

        tasks.customClimbTask.dependsOn tasks.withType(ClimbTask)
        project.tasks.customEchoTask.dependsOn tasks.withType(EchoTask)
        tasks.getByPath(":customJumpTask").dependsOn tasks.withType(JumpTask)
        """

        when:
        succeeds('customClimbTask', 'customEchoTask', 'customJumpTask')

        then:
        result.executedTasks.containsAll([':customClimbTask', ':climbTask', ':customJumpTask', ':jumpTask', ':customEchoTask', ':echoTask'])
    }

    @NotYetImplemented
    @Issue("GRADLE-3318")
    def "can reference rule-source tasks from sub-projects"() {
        given:
        def repo = file("maven").createDir()
        settingsFile << 'include "sub1", "sub2"'

        buildFile << """
        subprojects{
            apply plugin: "java"
            apply plugin: "maven-publish"

            publishing {
                repositories{ maven{ url '${normaliseFileSeparators(repo.getAbsolutePath())}'}}
                publications {
                    maven(MavenPublication) {
                        groupId 'org.gradle.sample'
                        version '1.1'
                        from components.java
                    }
                }
            }
        }

        task customPublish(dependsOn:  subprojects.collect { Project p -> p.tasks.withType(PublishToMavenLocal)})
"""
        when:
        succeeds('clean', 'build', 'customPublish')

        then:
        output.contains(":sub1:generatePomFileForMavenPublication")
        output.contains(":sub1:publishMavenPublicationToMavenRepository")
        output.contains(":sub2:generatePomFileForMavenPublication")
        output.contains(":sub2:publishMavenPublicationToMavenRepository")
        output.contains(":customPublish")
    }
}
