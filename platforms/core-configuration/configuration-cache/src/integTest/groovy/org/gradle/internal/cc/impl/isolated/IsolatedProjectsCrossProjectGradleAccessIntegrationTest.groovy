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
        invocation            | problemAccess
        "getPlugins()"        | "getPlugins"
        "apply([:])"          | "apply"
        "apply({})"           | "apply"
        "apply({} as Action)" | "apply"
        "getPluginManager()"  | "getPluginManager"

        combined:
        mode << ALL_MODES
    }

    def "reports a problem on project-level access to mutable state of the parent Gradle via #invocation"() {
        settingsFile """
            includeBuild("include")
        """
        file("include/build.gradle") << """
            gradle.parent.$invocation
        """

        when:
        isolatedProjectsFailsUsing mode, ":include:help"

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":include", ":")
            problem("Build file 'include/build.gradle': line 2: Project ':include' cannot access Gradle.$problemAccess on parent build ':'")
        }

        where:
        invocation             | problemAccess
        "getTaskGraph()"       | "getTaskGraph"
        "getStartParameter()"  | "getStartParameter"
        "getLifecycle()"       | "getLifecycle"
        "getProviders()"       | "getProviders"
        "getIncludedBuilds()"  | "getIncludedBuilds"
        "getPlugins()"         | "getPlugins"
        "getPluginManager()"   | "getPluginManager"
        "getExtensions()"      | "getExtensions"
        "getSettings()"        | "getSettings"
        "getServices()"        | "getServices"
        "getProjectRegistry()" | "getProjectRegistry"
        "rootProject {}"       | "rootProject"
        "allprojects {}"       | "allprojects"
        "beforeProject {}"     | "beforeProject"
        "afterProject {}"      | "afterProject"
        "apply([:])"           | "apply"

        combined:
        mode << ALL_MODES
    }

    def "allows project-level access to shared services and immutable data of the parent Gradle via #invocation"() {
        settingsFile """
            includeBuild("include")
        """
        file("include/build.gradle") << """
            gradle.parent.$invocation
        """

        when:
        isolatedProjectsRun ":include:help"

        then:
        fixture.assertStateStored {
            projectsConfigured(":include", ":")
        }

        where:
        invocation << [
            "getSharedServices()",
            "getGradleVersion()",
            "getGradleUserHomeDir()",
            "getGradleHomeDir()",
            "getBuildPath()",
            "getIdentityPath()",
            "isRootBuild()",
            "getPublicBuildPath()",
            "contextualize('something')",
        ]
    }
}
