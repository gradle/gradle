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

class RuleBasedTaskReferenceIntegrationTest extends AbstractIntegrationSpec {


    String echoTask = """
        class EchoTask extends DefaultTask {
            String text = "default"
            @TaskAction
            void print() {
                println(name + ' ' + text)
            }
        }
"""

    @NotYetImplemented
    def "an action is applied to a rule-source task "() {
        given:
        buildFile << """
        class OverruleTask extends EchoTask {}

        $echoTask

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("actionMan", EchoTask) {}
                tasks.create("overruleTask", OverruleTask) {
                 it.text = "Overruled!"
                }
            }
        }
        apply type: Rules

        tasks.withType(OverruleTask) {
            it.text = "actionWoman I'm the real commander"
        }

        //It should be possible to reference the task
        assert overruleTask.text == "actionWoman I'm the real commander"
        """

        when:
        succeeds('actionMan')

        then:
        output.contains("actionMan This is your commander speaking")
    }

    @NotYetImplemented
    def "a non-rule-source task can depend on a rule-source task "() {
        given:
        buildFile << """
        $echoTask

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("actionMan", EchoTask) {}
            }
        }
        apply type: Rules

        task actionWoman << {
            println "actionWoman I'm the real commander"
        }
        actionWoman.dependsOn tasks.withType(EchoTask)
        """

        when:
        succeeds('actionMan')

        then:
        output.contains("actionWoman I'm the real commander")
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
