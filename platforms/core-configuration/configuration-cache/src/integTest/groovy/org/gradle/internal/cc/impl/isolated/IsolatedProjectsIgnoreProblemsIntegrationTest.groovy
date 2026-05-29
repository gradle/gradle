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

package org.gradle.internal.cc.impl.isolated

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.util.internal.ToBeImplemented

/**
 * Tests for the "dangerously ignore problems" IP mode, where IP violations are reported but do not
 * fail the build, so a parallel build or sync can be timed before the violations are fixed.
 *
 * <p>Unlike the fail-fast and diagnostics modes (see {@link IsolatedProjectsAccessIntegrationTest}),
 * the build is expected to <em>succeed</em> here. The entry is still stored and then discarded
 * because problems were reported, so it is never reused by a later build.
 */
class IsolatedProjectsIgnoreProblemsIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    private static final String GROOVY_VIOLATION = "Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on subprojects via 'allprojects'"
    private static final String KOTLIN_VIOLATION = "Build file 'build.gradle.kts': Project ':' cannot access 'Project.plugins' functionality on subprojects via 'allprojects'"

    def "cross-project access succeeds and shows the banner instead of failing"() {
        given:
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
        """

        when:
        isolatedProjectsSucceedsIgnoringProblems("help")

        then:
        assertIgnoreProblemsBannerShownAtStartAndEnd("> Configure project :")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            projectsConfigured(":", ":a", ":b")
            problem(GROOVY_VIOLATION, 2)
        }
    }

    def "cross-project access in the Kotlin DSL succeeds and shows the banner"() {
        given:
        createDirs("a", "b")
        settingsKotlinFile << """
            include("a")
            include("b")
        """
        buildKotlinFile << """
            allprojects {
                plugins.apply("java-library")
            }
        """

        when:
        isolatedProjectsSucceedsIgnoringProblems("help")

        then:
        assertIgnoreProblemsBannerShownAtStartAndEnd("> Configure project :")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            projectsConfigured(":", ":a", ":b")
            problem(KOTLIN_VIOLATION, 2)
        }
    }

    def "discarded entry is not reused: a second run reconfigures and re-reports"() {
        given:
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
        """

        when:
        isolatedProjectsSucceedsIgnoringProblems("help")

        then:
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            projectsConfigured(":", ":a", ":b")
            problem(GROOVY_VIOLATION, 2)
        }

        when: "running again"
        isolatedProjectsSucceedsIgnoringProblems("help")

        then: "the entry was not reused, so the projects are reconfigured and the violation is reported again"
        assertIgnoreProblemsBannerShownAtStartAndEnd("> Configure project :")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            projectsConfigured(":", ":a", ":b")
            problem(GROOVY_VIOLATION, 2)
        }
    }

    def "is orthogonal to diagnostics: combined run still succeeds"() {
        given:
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
        """

        when:
        run(ENABLE_CLI, ENABLE_DANGEROUSLY_IGNORE_PROBLEMS, ENABLE_DIAGNOSTICS, "help")

        then:
        assertIgnoreProblemsBannerShownAtStartAndEnd("> Configure project :")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            projectsConfigured(":", ":a", ":b")
            problem(GROOVY_VIOLATION, 2)
        }
    }

    def "a clean build shows the banner but stores and reuses a normal cache entry"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << "task ok {}"

        when:
        isolatedProjectsSucceedsIgnoringProblems("ok")

        then:
        assertIgnoreProblemsBannerShownAtStartAndEnd("> Task :ok")
        fixture.assertStateStored {
            projectConfigured(":")
        }

        when: "running again with no violations"
        isolatedProjectsSucceedsIgnoringProblems("ok")

        then: "the entry is reused, but the banner is still shown"
        assertIgnoreProblemsBannerShownAtStartAndEnd("> Task :ok")
        fixture.assertStateLoaded()
    }

    def "dangerously-ignore-problems flag is part of the cache key"() {
        when:
        isolatedProjectsRun("help")
        then:
        fixture.assertStateStored {
            projectConfigured(":")
        }

        when:
        isolatedProjectsSucceedsIgnoringProblems("help")
        then:
        fixture.assertStateStored {
            projectConfigured(":")
        }

        // Now repeat invocations in different order to make sure both entries can be reused
        when:
        isolatedProjectsRun("help")
        then:
        fixture.assertStateLoaded()

        when:
        isolatedProjectsSucceedsIgnoringProblems("help")
        then:
        fixture.assertStateLoaded()
    }

    def "a genuine task failure still fails the build while the IP violation is only reported"() {
        given:
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
            tasks.register('boom') {
                doLast { throw new RuntimeException("boom from task") }
            }
        """

        when:
        fails(ENABLE_CLI, ENABLE_DANGEROUSLY_IGNORE_PROBLEMS, "boom")

        then:
        // assertHasDescription is a startsWith match; the real text continues " (registered in build file 'build.gradle')."
        failure.assertHasDescription("Execution failed for task ':boom'")
        failure.assertHasCause("boom from task")
        assertIgnoreProblemsBannerShownAtStartAndEnd("> Configure project :")
        outputContains(GROOVY_VIOLATION)
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            // The IP violations are Suppressed and reported separately, not attached to the :boom failure.
            reportedOutsideBuildFailure = true
            projectsConfigured(":", ":a", ":b")
            problem(GROOVY_VIOLATION, 2)
        }
    }

    def "the flag is inert when Isolated Projects is not enabled"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << "task ok { doLast {} }"

        when:
        run("-D${StartParameterBuildOptions.IsolatedProjectsDangerouslyIgnoreProblemsOption.PROPERTY_NAME}=true", "ok")

        then:
        result.assertTaskExecuted(":ok")
        outputDoesNotContain(DANGEROUSLY_IGNORE_PROBLEMS_BANNER)
        postBuildOutputDoesNotContain(DANGEROUSLY_IGNORE_PROBLEMS_BANNER)
    }

    @ToBeImplemented("problems=warn stores a reusable entry instead of discarding it under dangerously-ignore")
    def "combining the flag with problems=warn stores a reusable entry instead of discarding it"() {
        given:
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
        """

        when:
        run(ENABLE_CLI, ENABLE_DANGEROUSLY_IGNORE_PROBLEMS, WARN_PROBLEMS_CLI_OPT, "help")

        then:
        assertIgnoreProblemsBannerShownAtStartAndEnd("> Configure project :")
        outputContains(GROOVY_VIOLATION)
        // The dangerously-ignore mode reclassifies IP violations to Suppressed precisely so the entry is
        // discarded and never reused; warn mode currently defeats that, storing a reusable entry. This pins
        // the current (wrong) behavior. When fixed, this should read "discarded with 2 problems." and the
        // assertion should become fixture.assertStateStoredAndDiscarded { ... }.
        result.assertHasPostBuildOutput("Configuration cache entry stored with 2 problems.")
    }
}
