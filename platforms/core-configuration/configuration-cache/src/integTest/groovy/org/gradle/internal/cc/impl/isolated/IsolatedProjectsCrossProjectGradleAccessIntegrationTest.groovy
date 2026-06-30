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

import org.junit.Assume

class IsolatedProjectsCrossProjectGradleAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "reports a problem on project-level access of #gradleTarget Gradle.extensions via #invocation from #accessLocation"() {
        excludeRootParentVariant(gradleTarget, accessLocation)
        compositeBuild(accessLocation, """
            import org.gradle.api.reflect.TypeOf;

            interface Foo {}

            class DefaultFoo implements Foo {}

            ${gradleTarget.gradleAccess}.extensions.$invocation
        """)

        when:
        isolatedProjectsFailsUsing mode, accessLocation.task

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(*accessLocation.configuredProjects)
            problem("Build file '${accessLocation.buildFileName}': line 8: Project '${accessLocation.accessor}' cannot access Gradle.extensions")
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
        gradleTarget << GradleTarget.values().toList()

        combined:
        accessLocation << AccessLocation.values().toList()

        combined:
        mode << ALL_MODES
    }

    def "reports a problem on project-level access to mutable #gradleTarget Gradle state via #invocation from #accessLocation"() {
        excludeRootParentVariant(gradleTarget, accessLocation)
        compositeBuild(accessLocation, """
            ${gradleTarget.gradleAccess}.$invocation
        """)

        when:
        isolatedProjectsFailsUsing mode, accessLocation.task

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(*accessLocation.configuredProjects)
            problem("Build file '${accessLocation.buildFileName}': line 2: Project '${accessLocation.accessor}' cannot access Gradle.$problemAccess")
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
        gradleTarget << GradleTarget.values().toList()

        combined:
        accessLocation << AccessLocation.values().toList()

        combined:
        mode << ALL_MODES
    }

    def "reports a problem on project-level access to #gradleTarget Gradle.#invocation from #accessLocation"() {
        excludeRootParentVariant(gradleTarget, accessLocation)
        compositeBuild(accessLocation, """
            ${gradleTarget.gradleAccess}.$invocation
        """)

        // `useLogger` emits a deprecation warning when it runs (in DIAGNOSTICS); this test asserts IP problems,
        // not deprecations, so don't fail on it.
        executer.noDeprecationChecks()

        when:
        isolatedProjectsFailsUsing mode, accessLocation.task

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(*accessLocation.configuredProjects)
            expectedProblems(mode, accessLocation.accessor, gradleTarget)
                .each {
                    problem("Build file '${accessLocation.buildFileName}': line 2: $it")
                }
        }

        where:
        invocation                                          | expectedProblems
        "addListener(new Object())"                         | { IsolatedProjectsMode mode, String project, GradleTarget which -> expectedProblemsOnUnsupportedListener(mode, project, "addListener", which) }
        "addBuildListener(${buildListener()})"              | { IsolatedProjectsMode mode, String project, GradleTarget which -> expectedProblemsOnUnsupportedListener(mode, project, "addBuildListener", which) }
        "useLogger(new Object())"                           | { IsolatedProjectsMode mode, String project, GradleTarget which -> expectedProblemsOnUnsupportedListener(mode, project, "useLogger", which) }
        "removeListener(new Object())"                      | { IsolatedProjectsMode mode, String project, GradleTarget which -> ["Project '$project' cannot access Gradle.removeListener"] }
        "addListener(${projectEvaluationListener()})"       | { IsolatedProjectsMode mode, String project, GradleTarget which -> ["Project '$project' cannot access Gradle.addListener"] }
        "removeListener(${projectEvaluationListener()})"    | { IsolatedProjectsMode mode, String project, GradleTarget which -> ["Project '$project' cannot access Gradle.removeListener"] }
        "addListener(${taskExecutionGraphListener()})"      | { IsolatedProjectsMode mode, String project, GradleTarget which -> ["Project '$project' cannot access Gradle.addListener"] }
        "removeListener(${taskExecutionGraphListener()})"   | { IsolatedProjectsMode mode, String project, GradleTarget which -> ["Project '$project' cannot access Gradle.removeListener"] }
        "addListener(${dependencyResolutionListener()})"    | { IsolatedProjectsMode mode, String project, GradleTarget which -> ["Project '$project' cannot access Gradle.addListener"] }
        "removeListener(${dependencyResolutionListener()})" | { IsolatedProjectsMode mode, String project, GradleTarget which -> ["Project '$project' cannot access Gradle.removeListener"] }

        combined:
        gradleTarget << GradleTarget.values().toList()

        combined:
        accessLocation << AccessLocation.values().toList()

        combined:
        mode << ALL_MODES
    }

    enum AccessLocation {
        INCLUDED_BUILD("included build", ":included", "included/build.gradle", [":", ":included"], ":included:help"),
        BUILD_SRC("buildSrc", ":buildSrc", "buildSrc/build.gradle", [":buildSrc", ":"], ":buildSrc:help"),
        ROOT("root", ":", "build.gradle", [":"], ":help")

        final String description
        final String accessor
        final String buildFileName
        final List<String> configuredProjects
        final String task

        AccessLocation(String description, String accessor, String buildFileName, List<String> configuredProjects, String task) {
            this.description = description
            this.accessor = accessor
            this.buildFileName = buildFileName
            this.configuredProjects = configuredProjects
            this.task = task
        }

        @Override
        String toString() {
            description
        }
    }

    enum GradleTarget {
        CURRENT("current", "gradle"),
        PARENT("parent", "gradle.parent")

        final String description
        final String gradleAccess

        GradleTarget(String description, String gradleAccess) {
            this.description = description
            this.gradleAccess = gradleAccess
        }

        @Override
        String toString() {
            description
        }
    }

    private static void excludeRootParentVariant(GradleTarget gradleTarget, AccessLocation accessLocation) {
        // `gradle.parent` is null in the root build, so there is nothing to access via the parent there.
        Assume.assumeFalse(gradleTarget == GradleTarget.PARENT && accessLocation == AccessLocation.ROOT)
    }

    private void compositeBuild(AccessLocation accessLocation, String script) {
        settingsFile """
            if (${accessLocation == AccessLocation.INCLUDED_BUILD}) {
                includeBuild("included")
            }
        """
        file(accessLocation.buildFileName) << script
    }

    private static List<String> expectedProblemsOnUnsupportedListener(IsolatedProjectsMode mode, String accessor, String method, GradleTarget gradleTarget) {
        // In FAIL_FAST the build halts on the first (IP) problem, so only it is reported.
        if (mode == IsolatedProjectsMode.FAIL_FAST) {
            return ["Project '$accessor' cannot access Gradle.$method"]
        }
        // In DIAGNOSTICS, CC also reports the unsupported-listener registration, except in buildSrc,
        // which CC exempts (see ConfigurationCacheProblemsListener.isBuildSrcBuild).
        // That exemption only applies to direct access: via `gradle.parent` the listener registers on
        // the (root) parent build, which CC does not exempt, so the registration is still reported.
        (accessor == ":buildSrc" && gradleTarget == GradleTarget.CURRENT)
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
