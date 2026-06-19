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

class IsolatedProjectsCrossProjectGradleAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "reports a problem on project-level access of Gradle.extensions via #invocation"() {
        settingsFile """
            include("a")
        """
        file("a/build.gradle") << """
            import org.gradle.api.reflect.TypeOf;

            interface Foo {}

            class DefaultFoo implements Foo {}

            gradle.extensions.$invocation
        """

        when:
        isolatedProjectsFailsUsing mode, ":a:help"

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'a/build.gradle': line 8: Project ':a' cannot access Gradle.extensions")
        }

        where:
        invocation << [
            "add('foo', new DefaultFoo())",
            "add(Foo, 'foo', new DefaultFoo())",
            "add(TypeOf.typeOf(Foo), 'foo', new DefaultFoo())",

            "create('foo', DefaultFoo)",
            "create(Foo, 'foo', DefaultFoo)",
            "create(TypeOf.typeOf(Foo), 'foo', DefaultFoo)",

            // use ExtraPropertiesExtension as the only available by default
            "getByType(ExtraPropertiesExtension)",
            "getByType(TypeOf.typeOf(ExtraPropertiesExtension))",
            "findByType(ExtraPropertiesExtension)",
            "findByType(TypeOf.typeOf(ExtraPropertiesExtension))",

            "getByName('ext')",
            "findByName('ext')",

            "configure(ExtraPropertiesExtension) {}",
            "configure(TypeOf.typeOf(ExtraPropertiesExtension)) {}",
            "configure('ext') {}",

            "extraProperties",

            // Groovy dynamic access
            "ext",
            "foo = new DefaultFoo()",
        ]

        combined:
        mode << ALL_MODES
    }

    def "reports a problem on project-level access to mutable Gradle state via #invocation"() {
        settingsFile """
            include("a")
        """
        file("a/build.gradle") << """
            gradle.$invocation
        """

        when:
        isolatedProjectsFailsUsing mode, ":a:help"

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'a/build.gradle': line 2: Project ':a' cannot access Gradle.$problemAccess")
        }

        where:
        invocation                                                        | problemAccess
        "getPlugins()"                                                    | "getPlugins"
        "apply([:])"                                                      | "apply"
        "apply({})"                                                       | "apply"
        "apply({} as Action)"                                             | "apply"
        "getPluginManager()"                                              | "getPluginManager"

        "beforeProject({})"                                               | "beforeProject"
        "beforeProject({} as Action)"                                     | "beforeProject"
        "afterProject({})"                                                | "afterProject"
        "afterProject({} as Action)"                                      | "afterProject"
        "addProjectEvaluationListener(${projectEvaluationListener()})"    | "addProjectEvaluationListener"
        "removeProjectEvaluationListener(${projectEvaluationListener()})" | "removeProjectEvaluationListener"

        combined:
        mode << ALL_MODES
    }

    def "reports a problem on project-level access to Gradle.#invocation from #source"() {
        file(buildFileName) << """
            gradle.$invocation
        """
        settingsFile """
            if (${source == "included build"}) {
                includeBuild("included")
            }
        """

        // `useLogger` emits a deprecation warning when it runs (in DIAGNOSTICS); this test asserts IP problems,
        // not deprecations, so don't fail on it.
        executer.noDeprecationChecks()

        when:
        isolatedProjectsFailsUsing mode, "help"

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(*configuredProjects)
            expectedProblems(mode, accessor)
                .each {
                    problem("Build file '$buildFileName': line 2: $it")
                }
        }

        where:
        source                                              | buildFileName           | accessor    | configuredProjects
        "buildSrc"                                          | "buildSrc/build.gradle" | ":buildSrc" | [":buildSrc", ":"]
        "included build"                                    | "included/build.gradle" | ":included" | [":", ":included"]
        "root"                                              | "build.gradle"          | ":"         | [":"]

        combined:
        invocation                                          | expectedProblems
        "addListener(new Object())"                         | { IsolatedProjectsMode mode, String project -> expectedProblemsOnUnsupportedListener(mode, project, "addListener") }
        "buildFinished({})"                                 | { IsolatedProjectsMode mode, String project -> expectedProblemsOnUnsupportedListener(mode, project, "buildFinished") }
        "buildFinished({} as Action)"                       | { IsolatedProjectsMode mode, String project -> expectedProblemsOnUnsupportedListener(mode, project, "buildFinished") }
        "addBuildListener(${buildListener()})"              | { IsolatedProjectsMode mode, String project -> expectedProblemsOnUnsupportedListener(mode, project, "addBuildListener") }
        "useLogger(new Object())"                           | { IsolatedProjectsMode mode, String project -> expectedProblemsOnUnsupportedListener(mode, project, "useLogger") }
        "removeListener(new Object())"                      | { IsolatedProjectsMode mode, String project -> ["Project '$project' cannot access Gradle.removeListener"] }
        "addListener(${projectEvaluationListener()})"       | { IsolatedProjectsMode mode, String project -> ["Project '$project' cannot access Gradle.addListener"] }
        "removeListener(${projectEvaluationListener()})"    | { IsolatedProjectsMode mode, String project -> ["Project '$project' cannot access Gradle.removeListener"] }
        "addListener(${taskExecutionGraphListener()})"      | { IsolatedProjectsMode mode, String project -> ["Project '$project' cannot access Gradle.addListener"] }
        "removeListener(${taskExecutionGraphListener()})"   | { IsolatedProjectsMode mode, String project -> ["Project '$project' cannot access Gradle.removeListener"] }
        "addListener(${dependencyResolutionListener()})"    | { IsolatedProjectsMode mode, String project -> ["Project '$project' cannot access Gradle.addListener"] }
        "removeListener(${dependencyResolutionListener()})" | { IsolatedProjectsMode mode, String project -> ["Project '$project' cannot access Gradle.removeListener"] }

        combined:
        mode << ALL_MODES
    }

    private static List<String> expectedProblemsOnUnsupportedListener(IsolatedProjectsMode mode, String accessor, String method) {
        // In FAIL_FAST the build halts on the first (IP) problem, so only it is reported.
        if (mode == IsolatedProjectsMode.FAIL_FAST) {
            return ["Project '$accessor' cannot access Gradle.$method"]
        }
        // In DIAGNOSTICS, CC also reports the unsupported-listener registration, except in buildSrc,
        // which CC exempts (see ConfigurationCacheProblemsListener.isBuildSrcBuild).
        accessor == ":buildSrc"
            ? ["Project '$accessor' cannot access Gradle.$method"]
            : ["Project '$accessor' cannot access Gradle.$method", "registration of listener on 'Gradle.$method' is unsupported"]
    }

    private static String projectEvaluationListener() {
        """new org.gradle.api.ProjectEvaluationListener() { void beforeEvaluate(org.gradle.api.Project p) {}; void afterEvaluate(org.gradle.api.Project p, org.gradle.api.ProjectState s) {} }"""
    }

    private static String taskExecutionGraphListener() {
        """new org.gradle.api.execution.TaskExecutionGraphListener() { void graphPopulated(org.gradle.api.execution.TaskExecutionGraph g) {} }"""
    }

    private static String dependencyResolutionListener() {
        """new org.gradle.api.artifacts.DependencyResolutionListener() { void beforeResolve(org.gradle.api.artifacts.ResolvableDependencies d) {}; void afterResolve(org.gradle.api.artifacts.ResolvableDependencies d) {} }"""
    }

    private static String buildListener() {
        """new org.gradle.BuildAdapter() {}"""
    }
}
