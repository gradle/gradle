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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

// This tests current behaviour, not desired behaviour
class UndeclaredDependencyResolutionIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture {
    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "querying transform output of project artifacts without declaring this access emits deprecation"() {
        setupBuildWithProjectArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        expectUndeclaredTransformDeprecation()
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("result = [a.jar.green, b.jar.green]")

        when:
        expectUndeclaredTransformDeprecation()
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
        expectUndeclaredTransformDeprecation()
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

    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "querying chained transform output of project artifacts without declaring this access emits deprecation"() {
        setupBuildWithChainedProjectArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        expectUndeclaredTransformDeprecation()
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar", "a.jar.red", "b.jar.red")
        output.contains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        expectUndeclaredTransformDeprecation()
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

    private void expectUndeclaredTransformDeprecation() {
        // The message text must exactly match the deprecation emitted in
        // TransformedProjectArtifactSet#nagIfUndeclared (see DeprecationLogger.deprecateBehaviour call).
        executer.expectDocumentedDeprecationWarning(
            "Querying the output of an artifact transform of a project artifact " +
                "from a task action without declaring it as a task input has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. " +
                "Declare the FileCollection as a task input (for example via inputs.files(view)) " +
                "so the transform is wired into the execution plan. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_9.html#undeclared_artifact_transform_input"
        )
    }

}
