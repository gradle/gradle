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
import spock.lang.Unroll

import static org.gradle.util.TextUtil.normaliseFileSeparators

class RuleBasedTaskReferenceIntegrationTest extends AbstractIntegrationSpec {

    @NotYetImplemented
    @Unroll
    def "can apply an action to a rule task referenced via #reference"() {
        given:
        buildFile << """

        class EchoTask extends DefaultTask {
            String text = "default"

            @TaskAction
            void print() {
                println(name + ' ' + text)
            }
        }

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("actionMan", EchoTask) {
                }
            }
        }

        apply type: Rules

        task actionWoman << {
            println "actionWoman I'm really the commander"
        }

        actionWoman.dependsOn tasks.withType(EchoTask)

        $reference {
         it.text = 'This is your commander speaking'
        }
        """

        when:
        succeeds('actionMan')

        then:
        output.contains("actionMan This is your commander speaking")
        output.contains("actionWoman I'm the real commander")

        where:
        reference << ['tasks.withType(EchoTask)', "tasks.matching { it.name == 'actionMan'}.all() "]
    }

    @NotYetImplemented
    @Unroll
    def "can reference a model rule task created in a plugin"() {
        given:
        def source = file("src", "main", "java", "Something.java").createFile()
        def repo = file("maven").createDir()
        source.text = 'public class Something{}'

        buildFile << """
        apply plugin: "java"
        apply plugin: "maven-publish"

        publishing {
            repositories{ maven{ url '${normaliseFileSeparators(repo.getAbsolutePath())}'}}

            publications {
                maven(MavenPublication) {
                    groupId 'org.gradle.sample'
                    artifactId 'project1-sample'
                    version '1.1'
                    from components.java
                }
            }
        }

        task customPublish { }
        $reference
        """
        when:
        succeeds('clean', 'build', 'customPublish')

        then:
        output.contains(":generatePomFileForMavenPublication")
        output.contains(":publishMavenPublicationToMavenLocal")
        output.contains(":publishMavenPublicationToMavenRepository")
        output.contains(":customPublish")

        where:
        reference << [
            """
                    //Has no effect
                    customPublish.dependsOn tasks.withType(PublishToMavenLocal)
                    customPublish.dependsOn tasks.withType(PublishToMavenRepository)""",
            """
                    //Has no effect
                    afterEvaluate {
                          customPublish.dependsOn tasks.names.findAll { it.startsWith("publishMaven") }*.path
                    }
                """,
            """
                    //These cause a NPE on TaskMutator.mutate
                    customPublish.dependsOn tasks.findByName('publishMavenPublicationToMavenLocal')
                    customPublish.dependsOn tasks.findByName('publishMavenPublicationToMavenRepository')
                """
        ]
    }
}
