/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.resolve.attributes

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.resolve.VariantAwareDependencyResolutionTestFixture

class ProjectVariantResolutionIntegrationTest extends AbstractIntegrationSpec implements VariantAwareDependencyResolutionTestFixture, TasksWithInputsAndOutputs {
    def "does not realize tasks that produce outgoing artifacts that are not required"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorVariants()
        taskTypeWithOutputFileProperty()
        buildFile << """
            allprojects {
                def p = tasks.register("producer", FileProducer) {
                    output = file(project.name + ".txt")
                }
                def b = tasks.register("broken", FileProducer) {
                    throw new RuntimeException("broken task")
                }
                tasks.register("resolve", ShowFileCollection) {
                    def view = configurations.resolver.incoming.artifactView {
                        attributes.attribute(color, 'green')
                    }.files
                    files.from(view)
                }

                configurations {
                    broken {
                        assert canBeConsumed
                        canBeResolved = false
                        attributes.attribute(color, 'orange')
                        outgoing {
                            artifact(b.flatMap { it.output })
                            artifact(b.flatMap { throw new RuntimeException("broken outgoing artifact") })
                            variants {
                                create("broken") {
                                    artifact(b.flatMap { it.output })
                                    artifact(b.flatMap { throw new RuntimeException("broken variant artifact") })
                                }
                            }
                        }
                    }
                }

                artifacts {
                    implementation p.flatMap { it.output }
                    broken b.flatMap { it.output }
                    broken b.flatMap { throw new RuntimeException("broken artifact") }
                }
            }

            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }
        """

        when:
        run(":a:resolve")

        then:
        noExceptionThrown()
    }

    def "reports failure to realize tasks that produce outgoing artifacts"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorVariants()
        taskTypeWithOutputFileProperty()
        buildFile << """
            allprojects {
                def p = tasks.register("producer", FileProducer) {
                    throw new RuntimeException("broken")
                }
                tasks.register("resolve", ShowFileCollection) {
                    def view = configurations.resolver.incoming.artifactView {
                        attributes.attribute(color, 'green')
                    }.files
                    files.from(view)
                }
                configurations {
                    parent { }
                    implementation { extendsFrom parent }
                }
                $registerExpression
            }

            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a:resolve'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':a:resolver'.")
        failure.assertHasCause("Could not create task ':b:producer'.")
        failure.assertHasCause("broken")

        where:
        registerExpression                                                         | _
        "artifacts.implementation(p.flatMap { it.output })"                        | _
        "configurations.outgoing.outgoing.artifact(p.flatMap { it.output })" | _
        "artifacts.parent(p.flatMap { it.output })"                                | _
    }
}
