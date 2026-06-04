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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

// This tests current behaviour, not desired behaviour
class UndeclaredDependencyResolutionIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture {
    @ToBeFixedForConfigurationCache(because = "under CC, transform nodes for project artifacts are not serialized when the transform is not declared as a dependency of the task, causing 'project not found' errors during cache replay")
    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "task can query FileCollection containing the output of transform of project artifacts without declaring this access"() {
        setupBuildWithProjectArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("result = [a.jar.green, b.jar.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.green, b.jar.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "demonstrates CC bug: undeclared project-artifact transform output query fails with project-not-found"() {
        // Characterization test for the bug in issue #37219.
        //
        // Without Configuration Cache, querying the file collection from a `doLast` action triggers
        // the transform inline and succeeds (see the @ToBeFixedForConfigurationCache test above).
        //
        // With Configuration Cache enabled, the very first run already fails when the task action
        // resolves the artifact view: the producer project's state cannot be reached because the
        // transform was not declared as a task input (no edge in the work graph), so under CC
        // restrictions `ProjectStateRegistry.stateFor(...)` reports the producer project as missing.
        //
        // This test asserts the current broken behavior. Phase 1 of the fix replaces it with a
        // deprecation warning emitted before the failure; phase 2 turns the deprecation into a
        // hard error and removes the path entirely.

        setupBuildWithProjectArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        executer.withArgument("--configuration-cache")
        fails("broken")

        then:
        failure.assertHasDescription("Execution failed for task ':broken'")
        failure.assertHasCause("Could not resolve all files for configuration ':implementation'.")
        failure.assertThatCause(containsString("project ':a' not found."))
        failure.assertThatCause(containsString("project ':b' not found."))
    }

    @UnsupportedWithConfigurationCache(because = "task dependency logic is not executed when loaded from the configuration cache")
    def "can query FileCollection containing the output of transform of project artifacts at task graph calculation time"() {
        setupBuildWithProjectArtifactTransforms()
        taskQueriesFilesDuringTaskGraphCalculation()

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.count("result = [a.jar.green, b.jar.green]") == 2

        when:
        run("broken")

        then:
        assertTransformed()
        output.count("result = [a.jar.green, b.jar.green]") == 2
    }

    private void setupBuildWithProjectArtifactTransforms() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """

        setupBuildWithColorTransformImplementation(true)

        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "under CC, transform nodes for project artifacts are not serialized when the transform is not declared as a dependency of the task, causing 'project not found' errors during cache replay")
    def "task can query FileCollection containing the output of chained transform of project artifacts without declaring this access"() {
        setupBuildWithChainedProjectArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar", "a.jar.red", "b.jar.red")
        output.contains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.red.green, b.jar.red.green]")
    }

    @UnsupportedWithConfigurationCache(because = "task dependency logic is not executed when loaded from the configuration cache")
    def "can query FileCollection containing the output of chained transform of project artifacts at task graph calculation time"() {
        setupBuildWithChainedProjectArtifactTransforms()
        taskQueriesFilesDuringTaskGraphCalculation()

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar", "a.jar.red", "b.jar.red")
        output.count("result = [a.jar.red.green, b.jar.red.green]") == 2

        when:
        run("broken")

        then:
        assertTransformed()
        output.count("result = [a.jar.red.green, b.jar.red.green]") == 2
    }

    private void setupBuildWithChainedProjectArtifactTransforms() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """

        setupBuildWithChainedColorTransform(true)

        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }
        """
    }

    def "task can query FileCollection containing the output of transform of external artifacts without declaring this access"() {
        setupBuildWithExternalArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        run("broken")

        then:
        assertTransformed("one-1.0.jar", "two-1.0.jar")
        output.contains("result = [one-1.0.jar.green, two-1.0.jar.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [one-1.0.jar.green, two-1.0.jar.green]")
    }

    @UnsupportedWithConfigurationCache(because = "task dependency logic is not executed when loaded from the configuration cache")
    def "can query FileCollection containing the output of transform of external artifacts at task graph calculation time"() {
        setupBuildWithExternalArtifactTransforms()
        taskQueriesFilesDuringTaskGraphCalculation()

        when:
        run("broken")

        then:
        assertTransformed("one-1.0.jar", "two-1.0.jar")
        output.count("result = [one-1.0.jar.green, two-1.0.jar.green]") == 2

        when:
        run("broken")

        then:
        assertTransformed()
        output.count("result = [one-1.0.jar.green, two-1.0.jar.green]") == 2
    }

    private void setupBuildWithExternalArtifactTransforms() {
        withColorVariants(mavenRepo.module("one", "one")).publish()
        withColorVariants(mavenRepo.module("two", "two")).publish()

        setupBuildWithColorTransformImplementation()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'one:one:1.0'
                implementation 'two:two:1.0'
            }
        """
    }

    def "task can query FileCollection containing the output of chained transform of external artifacts without declaring this access"() {
        setupBuildWithChainedExternalArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        run("broken")

        then:
        assertTransformed("one-1.0.jar", "one-1.0.jar.red", "two-1.0.jar", "two-1.0.jar.red")
        output.contains("result = [one-1.0.jar.red.green, two-1.0.jar.red.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [one-1.0.jar.red.green, two-1.0.jar.red.green]")
    }

    @UnsupportedWithConfigurationCache(because = "task dependency logic is not executed when loaded from the configuration cache")
    def "can query FileCollection containing the output of chained transform of external artifacts at task graph calculation time"() {
        setupBuildWithChainedExternalArtifactTransforms()
        taskQueriesFilesDuringTaskGraphCalculation()

        when:
        run("broken")

        then:
        assertTransformed("one-1.0.jar", "one-1.0.jar.red", "two-1.0.jar", "two-1.0.jar.red")
        output.count("result = [one-1.0.jar.red.green, two-1.0.jar.red.green]") == 2

        when:
        run("broken")

        then:
        assertTransformed()
        output.count("result = [one-1.0.jar.red.green, two-1.0.jar.red.green]") == 2
    }

    private void setupBuildWithChainedExternalArtifactTransforms() {
        withColorVariants(mavenRepo.module("one", "one")).publish()
        withColorVariants(mavenRepo.module("two", "two")).publish()

        setupBuildWithChainedColorTransform()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'one:one:1.0'
                implementation 'two:two:1.0'
            }
        """
    }

    def "task can query FileCollection containing the output of transform of file dependency without declaring this access"() {
        setupBuildWithFileDepArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("result = [a.jar.green, b.jar.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.green, b.jar.green]")
    }

    @UnsupportedWithConfigurationCache(because = "task dependency logic is not executed when loaded from the configuration cache")
    def "can query FileCollection containing the output of transform of file dependency at task graph calculation time"() {
        setupBuildWithFileDepArtifactTransforms()
        taskQueriesFilesDuringTaskGraphCalculation()

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.count("result = [a.jar.green, b.jar.green]") == 2

        when:
        run("broken")

        then:
        assertTransformed()
        output.count("result = [a.jar.green, b.jar.green]") == 2
    }

    private void setupBuildWithFileDepArtifactTransforms() {
        setupBuildWithColorTransformImplementation(true)

        buildFile << """
            dependencies {
                implementation files("a.jar", "b.jar")
                artifactTypes {
                    jar {
                        attributes.attribute(color, 'blue')
                    }
                }
            }
        """
    }

    def "task can query FileCollection containing the output of chained transform of file dependency without declaring this access"() {
        setupBuildWithChainedFileDepArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "a.jar.red", "b.jar", "b.jar.red")
        output.contains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.red.green, b.jar.red.green]")
    }

    @UnsupportedWithConfigurationCache(because = "task dependency logic is not executed when loaded from the configuration cache")
    def "can query FileCollection containing the output of chained transform of file dependency at task graph calculation time"() {
        setupBuildWithChainedFileDepArtifactTransforms()
        taskQueriesFilesDuringTaskGraphCalculation()

        when:
        run("broken")

        then:
        assertTransformed("a.jar", "a.jar.red", "b.jar", "b.jar.red")
        output.count("result = [a.jar.red.green, b.jar.red.green]") == 2

        when:
        run("broken")

        then:
        assertTransformed()
        output.count("result = [a.jar.red.green, b.jar.red.green]") == 2
    }

    private void setupBuildWithChainedFileDepArtifactTransforms() {
        setupBuildWithChainedColorTransform(true)

        buildFile << """
            dependencies {
                implementation files("a.jar", "b.jar")
                artifactTypes {
                    jar {
                        attributes.attribute(color, 'blue')
                    }
                }
            }

        """
    }

    private void taskQueriesFilesWithoutDeclaringInput() {
        buildFile << """
            def view = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files

            task broken {
                doLast {
                    println "result = " + view.files.name
                }
            }
        """
    }

    private void taskQueriesFilesDuringTaskGraphCalculation() {
        buildFile << """
            def view = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files

            task broken {
                dependsOn {
                    println "result = " + view.files.name
                    []
                }
                doLast {
                    println "result = " + view.files.name
                }
            }
        """
    }

}
