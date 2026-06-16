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
            def message = overrideProblem ?: "Project ':a' cannot access Gradle.$problemAccess"
            problem("Build file 'a/build.gradle': line 2: $message")
        }

        where:
        invocation                                                        | problemAccess                     | overrideProblem
        "getPlugins()"                                                    | "getPlugins"                      | null
        "apply([:])"                                                      | "apply"                           | null
        "apply({})"                                                       | "apply"                           | null
        "apply({} as Action)"                                             | "apply"                           | null
        "getPluginManager()"                                              | "getPluginManager"                | null

        "beforeProject({})"                                               | "beforeProject"                   | null
        "beforeProject({} as Action)"                                     | "beforeProject"                   | null
        "afterProject({})"                                                | "afterProject"                    | null
        "afterProject({} as Action)"                                      | "afterProject"                    | null
        "addProjectEvaluationListener(${projectEvaluationListener()})"    | "addProjectEvaluationListener"    | null
        "removeProjectEvaluationListener(${projectEvaluationListener()})" | "removeProjectEvaluationListener" | null

        // CC unsupported listener
        "addListener(new Object())"                                       | "addListener"                     | "registration of listener on 'Gradle.addListener' is unsupported"
        "removeListener(new Object())"                                    | "removeListener"                  | null

        // CC supported listener
        "addListener(${projectEvaluationListener()})"                     | "addListener"                     | null
        "removeListener(${projectEvaluationListener()})"                  | "removeListener"                  | null

        combined:
        mode << ALL_MODES
    }

    private static String projectEvaluationListener() {
        """new org.gradle.api.ProjectEvaluationListener() { void beforeEvaluate(org.gradle.api.Project p) {}; void afterEvaluate(org.gradle.api.Project p, org.gradle.api.ProjectState s) {} }"""
    }
}
