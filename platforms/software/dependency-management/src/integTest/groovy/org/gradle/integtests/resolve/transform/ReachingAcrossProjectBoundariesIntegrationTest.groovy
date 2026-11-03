/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.UndeclaredArtifactTransformInputDeprecation
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.modes.UnsupportedWithIsolatedProjects
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

class ReachingAcrossProjectBoundariesIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture, UndeclaredArtifactTransformInputDeprecation {

    // This tests current behaviour, not desired behaviour
    @UnsupportedWithConfigurationCache(because = "Task does not declare that it uses the transform outputs")
    def "can consume transform outputs produced by another project without declaring this access"() {
        createDirs("a", "b", "sneaky")
        settingsFile << """
            include 'a', 'b', 'sneaky'
        """
        setupBuildWithColorTransformImplementation()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
                task prepare {
                    inputs.files configurations.resolver
                    doFirst {
                        configurations.resolver.files.name
                    }
                }
            }
            def files = project(':a').tasks.resolveArtifacts.collection.artifactFiles
            project(':sneaky') {
                task sneaky {
                    // Need to make sure that the configuration in the other project has been resolved but the transforms have not run yet
                    dependsOn(":a:prepare")
                    doLast {
                        println("result = " + files.files.name)
                    }
                }
            }
        """

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("sneaky:sneaky", "--parallel")

        then:
        outputContains("result = [b.jar.green]")
    }

    // This tests current behavior, not desired behavior
    @UnsupportedWithConfigurationCache(because = "Storing the task action resolves the configuration of another project, without the lock of that project")
    def "can consume transform outputs of external artifacts of another project without declaring this access"() {
        createDirs("a", "sneaky")
        settingsFile << """
            include 'a', 'sneaky'
        """
        mavenRepo.module("test", "test", "1.0")
            .adhocVariants()
            .variant("runtime", [color: "blue"])
            .withModuleMetadata()
            .publish()
        setupBuildWithColorTransformImplementation()
        buildFile << """
            project(":a") {
                ${mavenTestRepository()}
                dependencies {
                    implementation("test:test:1.0")
                }
                tasks.register("prepare") {
                    inputs.files(configurations.resolver)
                    doFirst {
                        configurations.resolver.files*.name
                    }
                }
            }
            def files = project(":a").tasks.resolveArtifacts.collection.artifactFiles
            project(":sneaky") {
                tasks.register("sneaky") {
                    // Need to make sure that the configuration in the other project has been resolved but the transforms have not run yet
                    dependsOn(":a:prepare")
                    doLast {
                        println("result = " + files.files.name.sort())
                    }
                }
            }
        """

        when:
        run("sneaky:sneaky", "--parallel")

        then:
        outputContains("result = [test-1.0.jar.green]")
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "Runs explicitly with and without parallel execution")
    @UnsupportedWithConfigurationCache(
        because = "Loading the task fails, as the project owning the configuration is not found",
        // With parallel execution, the build fails before storing the task, unless the owner resolved the configuration
        iterationMatchers = [".*external=true, (parallel=false|parallel=true, resolvedByOwner=true).*"]
    )
    @UnsupportedWithIsolatedProjects(because = "Consumes the configuration of another project")
    def "task declaring transform outputs of artifacts of another project as an input (external=#external, parallel=#parallel, resolvedByOwner=#resolvedByOwner)"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include("a", "b", "c")
        """
        (1..2).each {
            mavenRepo.module("test", "test$it", "1.0")
                .adhocVariants()
                .variant("runtime", [color: "blue"])
                .withModuleMetadata()
                .publish()
        }
        buildFile << """
            import org.gradle.api.artifacts.transform.TransformParameters

            def color = Attribute.of("color", String)

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    outputs.file(inputArtifact.get().asFile.name + ".green").text = "green"
                }
            }

            allprojects {
                ${mavenTestRepository()}
                dependencies {
                    attributesSchema {
                        attribute(color)
                    }
                    registerTransform(MakeGreen) {
                        from.attribute(color, "blue")
                        to.attribute(color, "green")
                    }
                }
            }

            project(":c") {
                file("c.jar").text = "c"
                configurations.consumable("outgoing") {
                    attributes.attribute(color, "blue")
                    outgoing.artifact(file("c.jar"))
                }
            }

            project(":a") {
                def scope = configurations.dependencyScope("deps")
                configurations.resolvable("depsPath") {
                    extendsFrom(scope)
                    attributes.attribute(color, "blue")
                }
                dependencies {
                    if ($external) {
                        deps("test:test1:1.0")
                        deps("test:test2:1.0")
                    } else {
                        deps(project(":c"))
                    }
                }
                ${resolvedByOwner ? "configurations.depsPath.incoming.resolutionResult.rootComponent.get()" : ""}
            }

            abstract class Consume extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getFiles()

                @TaskAction
                void consume() {
                    println("result = " + files.files*.name.sort())
                }
            }

            project(":b") {
                tasks.register("consume", Consume) {
                    files.from(project(":a").configurations.depsPath.incoming.artifactView {
                        attributes.attribute(color, "green")
                    }.files)
                }
            }
        """

        if (parallel) {
            executer.withArgument("--parallel")
        }

        expect:
        // With parallel execution, resolving the graph of ":a" for ":b" fails, unless ":a" resolved it before
        if (parallel && !resolvedByOwner) {
            fails(":b:consume")
            failure.assertHasCause("Resolution of the configuration ':a:depsPath' was attempted without an exclusive lock. This is unsafe and not allowed.")
        } else {
            run(":b:consume")
            outputContains("result = ${external ? "[test1-1.0.jar.green, test2-1.0.jar.green]" : "[c.jar.green]"}")
        }

        where:
        external | parallel | resolvedByOwner
        true     | true     | true
        true     | true     | false
        true     | false    | true
        true     | false    | false
        false    | true     | true
        false    | true     | false
        false    | false    | true
        false    | false    | false
    }

}
