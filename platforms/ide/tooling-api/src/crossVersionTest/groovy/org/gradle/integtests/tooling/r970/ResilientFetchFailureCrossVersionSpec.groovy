/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r970

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.integtests.tooling.r930.KotlinDslPluginRelatedToolingApiSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.IntermediateResultHandler

@ToolingApiVersion('>=9.7.0')
@TargetGradleVersion('>=9.7.0')
class ResilientFetchFailureCrossVersionSpec extends KotlinDslPluginRelatedToolingApiSpecification {

    private static final List<String> CONFIGURE_ON_DEMAND_ON = [
        "-Dorg.gradle.internal.isolated-projects.configure-on-demand=true",
        "-Dorg.gradle.unsafe.isolated-projects=true"
    ]

    private static final List<String> ISOLATED_PROJECTS_ON = [
        "-Dorg.gradle.isolated-projects=true"
    ]

    // The exact client-facing failure for a project with no failure of its own (clean or never-reached). Asserted in
    // full so this text is a guarantee: changing it must be a deliberate change that updates this test.
    private static final String GENERAL_CONFIGURATION_FAILURE = "The build could not be configured; see the reported build failures for the underlying problems."

    private FetchFailureTreeAction.Result fetchResult

    def setup() {
        settingsFile.delete()
        file('init.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

gradle.lifecycle.beforeProject {
    it.plugins.apply(CustomPlugin)
}

class CustomModel implements Serializable {
    String getValue() { 'greetings' }
}

class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        return new CustomModel()
    }
}

class CustomPlugin implements Plugin<Project> {
    @Inject
    CustomPlugin(ToolingModelBuilderRegistry registry) {
        registry.register(new CustomBuilder())
    }

    public void apply(Project project) {
    }
}
"""
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c")
            includeBuild("build-logic")
        """

        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"

            pluginManagement {
               $repositoriesBlock
            }

            dependencyResolutionManagement {
                $repositoriesBlock
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
        """
        included.file("src/main/kotlin/build-logic.gradle.kts") << """
            broken !!!
        """
        file("a/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """
        file("b/build.gradle.kts") << """
            plugins {
                id("build-logic")
            }
        """
        file("c/build.gradle.kts") << """
            plugins {
                id("build-logic")
            }
        """
    }

    def "an eager configuration failure is reported per project, clean and never-reached projects get a general message"() {
        when:
        fetchFailures()

        then: "the whole build fails to configure, so every project fails to be queried and the build fails"
        thrown(BuildException)
        def result = fetchResult
        result.failedToQueryProjects.toSet() == ['root', 'a', 'b', 'c'] as Set

        and: "eager fails fast at the first broken project, so only it has a recorded failure, and it is its own"
        def b = result.rootDescriptionByProject['b']
        b.contains(":b")
        !result.failureTreeByProject['b'].causes.isEmpty()
        b.contains("Caused by:")

        and: "clean and never-reached projects report a general message, not the whole-build aggregate"
        ['root', 'a', 'c'].each { p ->
            assert result.rootDescriptionByProject[p].contains(GENERAL_CONFIGURATION_FAILURE)
            assert !result.rootDescriptionByProject[p].contains(":b")
        }
    }

    def "configure-on-demand wrappers differ per project but the shared included build cause is identical"() {
        when:
        fetchFailures(CONFIGURE_ON_DEMAND_ON)

        then: "only the projects applying the broken convention plugin fail, each lazily"
        thrown(BuildException)
        def result = fetchResult
        result.failedToQueryProjects.toSet() == ['b', 'c'] as Set
        result.successfullyQueriedProjects.containsAll(['root', 'a', 'build-logic'])

        and: "the per project top wrappers differ and each names its own project path"
        def b = result.failureTreeByProject['b']
        def c = result.failureTreeByProject['c']
        b.message != c.message
        b.message.contains(":b")
        c.message.contains(":c")

        and: "each chain bottoms out at the same shared included build cause"
        b.deepest().message != null
        b.deepest().message == c.deepest().message
        b.message != b.deepest().message

        and: "the full description embeds the cause chain for each project"
        result.rootDescriptionByProject['b'].contains("Caused by:")
        result.rootDescriptionByProject['c'].contains("Caused by:")
    }

    def "a configuration failure attached to every fetched project fails the build only once"() {
        given: "a project failing eager configuration, with no other failure source"
        settingsKotlinFile.text = """
            rootProject.name = "root"
            include("a", "b", "c")
        """
        file("b/build.gradle.kts").text = """
            error("boom during configuration of b")
        """
        file("c/build.gradle.kts").text = """
            plugins {
                id("java")
            }
        """

        when:
        fetchFailures()

        then: "the same configuration failure is delivered to every project's fetch result"
        def e = thrown(BuildException)
        fetchResult.failedToQueryProjects.toSet() == ['root', 'a', 'b', 'c'] as Set

        and: "the build fails with that failure only once"
        countCauseMessages(e, "boom during configuration of b") == 1
    }

    def "each failing project reports only its own failure and clean projects report neither, while the build still fails"() {
        given: "two independently failing projects next to a clean one, no other failure source"
        settingsKotlinFile.text = """
            rootProject.name = "root"
            include("a", "b", "c")
        """
        file("a/build.gradle.kts").text = "// intentionally clean\n"
        file("b/build.gradle.kts").text = 'throw RuntimeException("FAILURE(:b)")\n'
        file("c/build.gradle.kts").text = 'throw RuntimeException("FAILURE(:c)")\n'

        when:
        fetchFailures(ISOLATED_PROJECTS_ON)

        then: "behaviour is unchanged: the whole build fails to configure, so every project fails to be queried and the build fails"
        thrown(BuildException)
        def result = fetchResult
        result.successfullyQueriedProjects == []
        result.failedToQueryProjects.toSet() == ["root", "a", "b", "c"] as Set

        and: "each failing project's client failure carries only its own marker, not the sibling's whole-build aggregate"
        treeContains(result, "b", "FAILURE(:b)")
        !treeContains(result, "b", "FAILURE(:c)")
        treeContains(result, "c", "FAILURE(:c)")
        !treeContains(result, "c", "FAILURE(:b)")

        and: "clean projects carry exactly the general message and nothing else"
        ["root", "a"].each { project ->
            def node = result.failureTreeByProject[project]
            assert node.message == GENERAL_CONFIGURATION_FAILURE
            assert node.causes.isEmpty()
        }
    }

    def "failures thrown from lifecycle hooks are reported as the failure of the project they configure"() {
        given: "projects failing from hooks around their configuration, next to a clean one"
        settingsKotlinFile.text = """
            rootProject.name = "root"
            include("a", "b", "c")

            gradle.lifecycle.beforeProject {
                if (name == "b") {
                    throw RuntimeException("FAILURE(:b)")
                }
            }
        """
        file("a/build.gradle.kts").text = "// intentionally clean\n"
        file("b/build.gradle.kts").text = "// intentionally clean, fails from the beforeProject hook\n"
        file("c/build.gradle.kts").text = 'afterEvaluate { throw RuntimeException("FAILURE(:c)") }\n'

        when:
        fetchFailures(ISOLATED_PROJECTS_ON)

        then: "the whole build fails to configure, so every project fails to be queried and the build fails"
        thrown(BuildException)
        def result = fetchResult
        result.successfullyQueriedProjects == []
        result.failedToQueryProjects.toSet() == ["root", "a", "b", "c"] as Set

        and: "each hook failure is carried by the project being configured, not by its siblings"
        treeContains(result, "b", "FAILURE(:b)")
        !treeContains(result, "b", "FAILURE(:c)")
        treeContains(result, "c", "FAILURE(:c)")
        !treeContains(result, "c", "FAILURE(:b)")

        and: "clean projects carry exactly the general message and nothing else"
        ["root", "a"].each { project ->
            def node = result.failureTreeByProject[project]
            assert node.message == GENERAL_CONFIGURATION_FAILURE
            assert node.causes.isEmpty()
        }
    }

    def "a build-scoped configuration failure fails the build but no project reports it as its own"() {
        given: "a failure raised by a build-scoped hook after every project configured cleanly"
        settingsKotlinFile.text = """
            rootProject.name = "root"
            include("a", "b", "c")
        """
        file("build.gradle.kts").text = 'gradle.projectsEvaluated { throw RuntimeException("FAILURE(build)") }\n'
        file("a/build.gradle.kts").text = "// intentionally clean\n"
        file("b/build.gradle.kts").text = "// intentionally clean\n"
        file("c/build.gradle.kts").text = "// intentionally clean\n"

        when:
        fetchFailures()

        then: "the whole build fails to configure, so every project fails to be queried and the build fails"
        def e = thrown(BuildException)
        def result = fetchResult
        result.successfullyQueriedProjects == []
        result.failedToQueryProjects.toSet() == ["root", "a", "b", "c"] as Set

        and: "the failure belongs to no single project, so every project reports the general message"
        ["root", "a", "b", "c"].each { project ->
            assert treeContains(result, project, GENERAL_CONFIGURATION_FAILURE)
            assert !treeContains(result, project, "FAILURE(build)")
        }

        and: "the build still fails with the build-scoped failure: once from the build-scoped GradleBuild fetch and once shared by all project fetches"
        countCauseMessages(e, "FAILURE(build)") == 2
    }

    private void fetchFailures(List<String> extraGradleProperties = []) {
        fails {
            action()
                .buildFinished(new FetchFailureTreeAction(CustomModel), { fetchResult = it } as IntermediateResultHandler)
                .build()
                .withArguments("--init-script=${file('init.gradle').absolutePath}", *extraGradleProperties)
                .run()
        }
    }

    /**
     * Counts how many failures in the whole failure tree carry the given message, following all causes of
     * multi-cause exceptions rather than just the first one.
     */
    private static int countCauseMessages(Throwable throwable, String text, int depth = 0) {
        if (throwable == null || depth > 50) {
            return 0
        }
        int count = throwable.message == text ? 1 : 0
        def causes = throwable.respondsTo('getCauses')
            ? throwable.causes
            : (throwable.cause != null ? [throwable.cause] : [])
        causes.each { count += countCauseMessages(it as Throwable, text, depth + 1) }
        return count
    }

    private static boolean treeContains(FetchFailureTreeAction.Result result, String project, String marker) {
        return nodeContains(result.failureTreeByProject[project], marker)
    }

    private static boolean nodeContains(FetchFailureTreeAction.FailureNode node, String marker) {
        if (node == null) {
            return false
        }
        if ((node.message ?: "").contains(marker)) {
            return true
        }
        return node.causes.any { nodeContains(it, marker) }
    }
}
