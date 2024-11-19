/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.spockframework.lang.Wildcard

class UnsafeConfigurationResolutionDeprecationIntegrationTest extends AbstractDependencyResolutionTest {
    def "configuration in another project produces deprecation warning when resolved"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        createDirs("bar")
        settingsFile << """
            rootProject.name = "foo"
            include ":bar"
        """

        buildFile << """
            task resolve {
                def otherProjectConfiguration = provider {
                    project(':bar').configurations.bar
                }
                doLast {
                    println otherProjectConfiguration.get().files
                }
            }

            project(':bar') {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }

                configurations {
                    bar
                }

                dependencies {
                    bar "test:test-jar:1.0"
                }
            }
        """
        executer.withArgument("--parallel")

        expect:
        executer.expectDocumentedDeprecationWarning("Resolution of the configuration :bar:bar was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved. This behavior has been deprecated. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors in the Gradle documentation.")
        succeeds(":resolve")
    }

    private String declareRunInAnotherThread() {
        """
        def runInAnotherThread = { toRun ->
            def failure = null
            def result = null
            def thread = new Thread({
                try {
                    result = toRun.call()
                } catch (Throwable t) {
                    failure = t
                }
            })
            thread.start()
            thread.join()
            return failure ?: result
        }
        """
    }

    def "exception when non-gradle thread resolves dependency graph"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
        """

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            configurations {
                bar
            }

            dependencies {
                bar "test:test-jar:1.0"
            }

            ${declareRunInAnotherThread()}

            task resolve {
                def failure = provider {
                    runInAnotherThread.call {
                        configurations.bar.${expression}
                    }
                }
                doFirst {
                    assert failure.isPresent()
                    assert (failure.get() instanceof Throwable)
                    throw failure.get()
                }
            }
        """

        when:
        if (expression == "files { true }" ) {
            executer.expectDocumentedDeprecationWarning("The Configuration.files(Closure) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use Configuration.getIncoming().artifactView(Action) with a componentFilter instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_filtered_configuration_file_and_filecollection_methods")
        } else if (expression == "fileCollection { true }.files") {
            executer.expectDocumentedDeprecationWarning("The Configuration.fileCollection(Closure) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use Configuration.getIncoming().artifactView(Action) with a componentFilter instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_filtered_configuration_file_and_filecollection_methods")
        }
        fails(":resolve")

        then:
        failure.assertHasFailure("Execution failed for task ':resolve'.") {
            it.assertHasCause("The configuration :bar was resolved from a thread not managed by Gradle.")
        }

        where:
        expression << [
            "files",
            "incoming.resolutionResult.root",
            "incoming.resolutionResult.rootComponent.get()",
            "incoming.artifacts.artifactFiles.files",
            "incoming.artifacts.artifacts",
            "incoming.artifactView { }.files.files",
            "incoming.artifactView { }.artifacts.artifacts",
            "incoming.artifactView { }.artifacts.resolvedArtifacts.get()",
            "incoming.artifactView { }.artifacts.failures",
            "incoming.artifactView { }.artifacts.artifactFiles.files",
            "resolve()",
            "files { true }",
            "fileCollection { true }.files",
            "resolvedConfiguration.files",
            "resolvedConfiguration.resolvedArtifacts"
        ]
    }

    def "no exception when non-gradle thread iterates over dependency artifacts that were declared as task inputs"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
        """

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            configurations {
                bar
            }

            dependencies {
                bar "test:test-jar:1.0"
            }

            ${declareRunInAnotherThread()}

            task resolve {
                def configuration = configurations.bar
                inputs.files(configuration)
                def layout = project.layout
                def traversal = provider { configuration.${expression} }
                doFirst {
                    runInAnotherThread.call {
                        layout.projectDirectory.file('bar').asFile << traversal.get()
                    }
                }
            }
        """

        when:
        if (expression == "files { true }") {
            executer.expectDocumentedDeprecationWarning("The Configuration.files(Closure) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use Configuration.getIncoming().artifactView(Action) with a componentFilter instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_filtered_configuration_file_and_filecollection_methods")
        } else if (expression == "fileCollection { true }.files") {
            executer.expectDocumentedDeprecationWarning("The Configuration.fileCollection(Closure) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use Configuration.getIncoming().artifactView(Action) with a componentFilter instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_filtered_configuration_file_and_filecollection_methods")
        } else if (expression == "resolvedConfiguration.files") {
            executer.expectDocumentedDeprecationWarning("The ResolvedConfiguration.getFiles() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use Configuration#getFiles instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_legacy_configuration_get_files")
        }

        def shouldSucceed = ccMessage instanceof Wildcard || !GradleContextualExecuter.isConfigCache()
        if (shouldSucceed) {
            run(":resolve")
        } else {
            fails(":resolve")
        }

        then:
        if (shouldSucceed) {
            result.assertTaskExecuted(":resolve")
        } else {
            result.assertHasErrorOutput(ccMessage as String)
        }

        where:
        expression                                                      | ccMessage
        "files"                                                         | _
        "incoming.resolutionResult.root"                                | _
        "incoming.resolutionResult.rootComponent.get()"                 | _
        "incoming.artifacts.artifactFiles.files"                        | _
        "incoming.artifacts.artifacts"                                  | "org.gradle.api.artifacts.result.ArtifactResult"
        "incoming.artifactView { }.files.files"                         | _
        "incoming.artifactView { }.artifacts.artifacts"                 | "org.gradle.api.artifacts.result.ArtifactResult"
        "incoming.artifactView { }.artifacts.resolvedArtifacts.get()"   | "org.gradle.api.artifacts.result.ArtifactResult"
        "incoming.artifactView { }.artifacts.failures"                  | _
        "incoming.artifactView { }.artifacts.artifactFiles.files"       | _
        "resolve()"                                                     | _
        "files { true }"                                                | _
        "fileCollection { true }.files"                                 | _
        "resolvedConfiguration.files"                                   | _
        "resolvedConfiguration.resolvedArtifacts"                       | "org.gradle.api.artifacts.ResolvedArtifact"
    }

    def "no exception when non-gradle thread iterates over dependency artifacts that were previously iterated"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
        """

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            configurations {
                bar
            }

            dependencies {
                bar "test:test-jar:1.0"
            }

            task resolve {
                def configuration = configurations.bar
                def outFile = file('bar')
                doFirst {
                    configuration.files
                    def failure = null
                    def thread = new Thread({
                        try {
                            outFile << configuration.files
                        } catch(Throwable t) {
                            failure = t
                        }
                    })
                    thread.start()
                    thread.join()
                    if (failure != null) {
                        throw failure
                    }
                }
            }
        """

        expect:
        succeeds(":resolve")
    }

    def "deprecation warning when configuration is resolved while evaluating a different project"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        createDirs("bar", "baz")
        settingsFile << """
            rootProject.name = "foo"
            include ":bar", ":baz"
        """

        buildFile << """
            project(':baz') {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }

                configurations {
                    baz
                }

                dependencies {
                    baz "test:test-jar:1.0"
                }
            }

            project(':bar') {
                println project(':baz').configurations.baz.files
            }
        """
        executer.withArgument("--parallel")

        expect:
        executer.expectDocumentedDeprecationWarning("Resolution of the configuration :baz:baz was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved. This behavior has been deprecated. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors in the Gradle documentation.")
        succeeds(":bar:help")
    }

    def "no deprecation warning when configuration is resolved while evaluating same project"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        createDirs("bar")
        settingsFile << """
            rootProject.name = "foo"
            include ":bar"
        """

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            configurations {
                foo
            }

            dependencies {
                foo "test:test-jar:1.0"
            }

            println configurations.foo.files
        """

        expect:
        executer.withArgument("--parallel")
        succeeds(":bar:help")
    }

    def "no deprecation warning when configuration is resolved while evaluating afterEvaluate block"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
        """

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            configurations {
                foo
            }

            dependencies {
                foo "test:test-jar:1.0"
            }

            afterEvaluate {
                println configurations.foo.files
            }
        """

        expect:
        executer.withArgument("--parallel")
        succeeds(":help")
    }

    def "no deprecation warning when configuration is resolved while evaluating beforeEvaluate block"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
        """

        file('init-script.gradle') << """
            allprojects {
                beforeEvaluate {
                    repositories {
                        maven { url '${mavenRepo.uri}' }
                    }

                    configurations {
                        foo
                    }

                    dependencies {
                        foo "test:test-jar:1.0"
                    }

                    println configurations.foo.files
                }
            }
        """

        expect:
        executer.withArguments("--parallel", "-I", "init-script.gradle")
        succeeds(":help")
    }
}
