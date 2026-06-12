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
import org.gradle.integtests.fixtures.modes.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.UndeclaredArtifactTransformInputDeprecation
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

class UndeclaredDependencyResolutionIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture, UndeclaredArtifactTransformInputDeprecation {
    @ToBeFixedForConfigurationCache(because = "Emits a deprecation warning, but under CC the underlying 'project not found' failure still occurs after the warning")
    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "querying transform output of project artifacts without declaring this access emits deprecation"() {
        setupBuildWithProjectArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("result = [a.jar.green, b.jar.green]")

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("broken")

        then:
        assertTransformed()
        output.contains("result = [a.jar.green, b.jar.green]")
    }

    @UnsupportedWithConfigurationCache(because = "explicitly enables Configuration Cache in the test body to demonstrate the project-not-found failure")
    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "demonstrates CC bug: undeclared project-artifact transform output query fails with project-not-found"() {
        // Characterization test for the bug in issue #37219.
        //
        // With Configuration Cache enabled, the very first run already fails when the task action
        // resolves the artifact view: the producer project's state cannot be reached because the
        // transform was not declared as a task input (no edge in the work graph), so under CC
        // restrictions `ProjectStateRegistry.stateFor(...)` reports the producer project as missing.
        //
        // A deprecation warning is currently emitted before the failure. Eventually this access
        // pattern should produce a hard error before the project lookup, replacing the cryptic
        // "project not found" with a clear up-front diagnostic.

        setupBuildWithProjectArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        executer.withArgument("--configuration-cache")
        expectUndeclaredArtifactTransformInputDeprecation()
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
        // Both the dependsOn closure query (during task graph calculation) and the doLast query
        // (during execution) reach the same TransformStepNodes, but the per-(node, task)
        // naggedTaskPaths set dedupes them to a single warning per task per node per build.
        expectUndeclaredArtifactTransformInputDeprecation()
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar")
        output.count("result = [a.jar.green, b.jar.green]") == 2

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
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

    @ToBeFixedForConfigurationCache(because = "Emits a deprecation warning, but under CC the underlying 'project not found' failure still occurs after the warning")
    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "task A's undeclared query emits deprecation when run alone, even though task B in the same build correctly declares the same transform output"() {
        // Characterizes that a sibling task B's correct declaration does NOT silence the nag for
        // task A when A is invoked on its own. B never enters the work graph (it isn't invoked),
        // so the post-graph BFS in DefaultExecutionPlan only walks taskA's subgraph and does not
        // mark the transform nodes as declared by taskA. At execution time, taskA's inline query
        // finds itself absent from each node's declaringTaskPaths set and the nag fires.

        setupBuildWithProjectArtifactTransforms()
        twoTasksSharingViewOnlyOneDeclaresIt()

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("taskA")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("taskA result = [a.jar.green, b.jar.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "task A's undeclared query still emits deprecation when triggered as a dependency of task B that correctly declares the same transform output"() {
        // Running task B triggers task A via dependsOn. The nag is gated on per-task declaration:
        // DefaultExecutionPlan walks dependency successors from every LocalTaskNode (stopping at
        // other task boundaries) and marks reachable TaskDeclarationAware nodes. taskB's declared
        // input reaches the transform nodes, but the walk does not cross the boundary into taskA's
        // subgraph, so taskA is not marked. At execution time, taskA's inline view query fires the
        // nag regardless of scheduler ordering.

        setupBuildWithProjectArtifactTransforms()
        twoTasksSharingViewOnlyOneDeclaresIt()

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("taskB")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("taskA result = [a.jar.green, b.jar.green]")
        output.contains("taskB result = [a.jar.green, b.jar.green]")
    }

    private void twoTasksSharingViewOnlyOneDeclaresIt() {
        buildFile << """
            def view = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files

            // task A queries the view from a doLast action WITHOUT declaring it as an input.
            task taskA {
                doLast {
                    println "taskA result = " + view.files.name
                }
            }

            // task B properly declares the view as an input AND dependsOn task A so that
            // running B triggers A as well.
            task taskB {
                inputs.files(view)
                dependsOn taskA
                doLast {
                    println "taskB result = " + view.files.name
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "Emits a deprecation warning, but under CC the underlying 'project not found' failure still occurs after the warning")
    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "task B's undeclared query emits deprecation when run alone, even though task A in the same build correctly declares the same transform output"() {
        // Mirror image of "task A's undeclared query emits deprecation when run alone...":
        // the well-behaved declarer (now A) sits in the build but never enters the work graph
        // because we invoke only B. Without A's declared input scheduling the transforms,
        // B's inline view query reaches the transform nodes first and fires the nag.

        setupBuildWithProjectArtifactTransforms()
        twoTasksSharingViewOnlyADeclaresIt(false)

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("taskB")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("taskB result = [a.jar.green, b.jar.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "task B's undeclared query emits deprecation when triggered as a downstream of task A that correctly declares the transform output"() {
        // Per-task declaration check: even though task A's declared input puts the transform
        // nodes into A's dependency subgraph, the post-graph BFS does not propagate that
        // declaration across the task boundary when walking from taskB. taskB is not in any
        // node's declaringTaskPaths set, so B's doLast query fires the nag.

        setupBuildWithProjectArtifactTransforms()
        twoTasksSharingViewOnlyADeclaresIt(true)

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("taskB")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("taskA result = [a.jar.green, b.jar.green]")
        output.contains("taskB result = [a.jar.green, b.jar.green]")
    }

    private void twoTasksSharingViewOnlyADeclaresIt(boolean bDependsOnA) {
        buildFile << """
            def view = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files

            // task A properly declares the view as an input AND queries it from its action.
            task taskA {
                inputs.files(view)
                doLast {
                    println "taskA result = " + view.files.name
                }
            }

            // task B does NOT declare the view as an input but queries it from its action.
            task taskB {
                ${bDependsOnA ? "dependsOn taskA" : ""}
                doLast {
                    println "taskB result = " + view.files.name
                }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "task A's undeclared query emits deprecation when both A and B (which declares the transform) are invoked, with A requested first"() {
        // No dependsOn between A and B. Both are invoked. The per-task declaration check is
        // computed at execution-plan-determination time before any task runs: taskB's declared
        // input puts taskB into the transform nodes' declaringTaskPaths set, taskA's lack of
        // declaration leaves taskA out. At execution time, taskA's inline view query fires the
        // nag. --max-workers=1 only constrains parallelism; scheduler ordering does not
        // influence declaration attribution.

        setupBuildWithProjectArtifactTransforms()
        twoTasksSharingViewOnlyBDeclaresItNoLink()

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("taskA", "taskB", "--max-workers=1")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("taskA result = [a.jar.green, b.jar.green]")
        output.contains("taskB result = [a.jar.green, b.jar.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "task A's undeclared query emits deprecation when both A and B (which declares the transform) are invoked, with B requested first"() {
        // No dependsOn between A and B. Even with taskB listed first on the command line, the
        // per-task declaration check is computed at execution-plan-determination time before any
        // task runs. taskB is in the transform nodes' declaringTaskPaths set; taskA is not.
        // At execution time, taskA's inline view query fires the nag regardless of which task
        // the scheduler ran first.

        setupBuildWithProjectArtifactTransforms()
        twoTasksSharingViewOnlyBDeclaresItNoLink()

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("taskB", "taskA", "--max-workers=1")

        then:
        assertTransformed("a.jar", "b.jar")
        output.contains("taskA result = [a.jar.green, b.jar.green]")
        output.contains("taskB result = [a.jar.green, b.jar.green]")
    }

    private void twoTasksSharingViewOnlyBDeclaresItNoLink() {
        buildFile << """
            def view = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files

            // task A does NOT declare the view as an input but queries it from its action.
            task taskA {
                doLast {
                    println "taskA result = " + view.files.name
                }
            }

            // task B properly declares the view as an input AND queries it from its action.
            // No dependsOn between A and B in this variant.
            task taskB {
                inputs.files(view)
                doLast {
                    println "taskB result = " + view.files.name
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "Emits a deprecation warning, but under CC the underlying 'project not found' failure still occurs after the warning")
    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "querying transform output of dependency-requiring transform names the offending task and configuration"() {
        // When the transform declares @InputArtifactDependencies, the upstream resolver carries
        // through the originating configuration's identity, so the deprecation includes a
        // contextual line identifying the offending task and configuration.
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                    }
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifactDependencies
                abstract FileCollection getInputArtifactDependencies()
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()
                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    def output = outputs.file(input.name + ".green")
                    if (input.file) {
                        output.text = input.text + ".green"
                    } else {
                        output.text = "missing.green"
                    }
                }
            }

            dependencies {
                implementation project(':a')
                implementation project(':b')
            }

            def view = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files

            task broken {
                doLast {
                    println "result = " + view.files.name
                }
            }
        """

        when:
        expectUndeclaredArtifactTransformInputDeprecation(":broken", "implementation")
        run("broken")

        then:
        output.contains("result = [a.jar.green, b.jar.green]")
    }

    @ToBeFixedForConfigurationCache(because = "under CC the underlying 'project not found' failure still occurs after the deprecation warning")
    @Issue("https://github.com/gradle/gradle/issues/37219")
    def "querying chained transform output of project artifacts without declaring this access emits deprecation"() {
        setupBuildWithChainedProjectArtifactTransforms()
        taskQueriesFilesWithoutDeclaringInput()

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar", "a.jar.red", "b.jar.red")
        output.contains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
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
        // Both the dependsOn closure query (during task graph calculation) and the doLast query
        // (during execution) reach the same TransformStepNodes, but the per-(node, task)
        // naggedTaskPaths set dedupes them to a single warning per task per node per build.
        expectUndeclaredArtifactTransformInputDeprecation()
        run("broken")

        then:
        assertTransformed("a.jar", "b.jar", "a.jar.red", "b.jar.red")
        output.count("result = [a.jar.red.green, b.jar.red.green]") == 2

        when:
        expectUndeclaredArtifactTransformInputDeprecation()
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
