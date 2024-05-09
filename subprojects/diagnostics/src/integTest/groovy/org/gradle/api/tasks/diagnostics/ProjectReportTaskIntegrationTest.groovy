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
package org.gradle.api.tasks.diagnostics

import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.declarativedsl.settings.SoftwareTypeFixture

class ProjectReportTaskIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture {

    def "reports project structure with single composite"() {
        given:
        createDirs("p1", "p2", "p2/p22", "another")
        file("settings.gradle") << """rootProject.name = 'my-root-project'
include('p1')
include('p2')
include('p2:p22')
includeBuild('another')"""
        file('another/settings.gradle').touch()

        when:
        run ":projects"

        then:
        outputContains """
Root project 'my-root-project'
+--- Project ':p1'
\\--- Project ':p2'
     \\--- Project ':p2:p22'

Included builds
\\--- Included build ':another'
"""
    }

    def "reports project structure with transitive composite"() {
        given:
        createDirs("third", "third/t1")
        file("settings.gradle") << """rootProject.name = 'my-root-project'
includeBuild('another')"""
        file('another/settings.gradle') << "includeBuild('../third')"
        file('third/settings.gradle') << "include('t1')"

        when:
        run ":projects"

        then:
        outputContains """
Root project 'my-root-project'
No sub-projects

Included builds
+--- Included build ':another'
\\--- Included build ':third'"""
    }

    def "included builds are only shown in the context of the root project"() {
        given:
        createDirs("p1", "p1/p11", "another")
        file("settings.gradle") << """rootProject.name = 'my-root-project'
include('p1')
include('p1:p11')
includeBuild('another')"""
        file("p1").mkdir()
        file('another/settings.gradle') << ""

        when:
        projectDir("p1")
        run "projects"

        then:
        outputDoesNotContain "another"
    }

    def "included builds are only rendered if there are some"() {
        when:
        run "projects"
        then:
        outputDoesNotContain("Included builds")
    }

    def "rendering long project descriptions is sensible"() {
        settingsFile << "rootProject.name = 'my-root-project'"
        buildFile << """
            description = '''
this is a long description

this shouldn't be visible
            '''
        """
        when:
        run "projects"
        then:
        outputContains """
Root project 'my-root-project' - this is a long description...
No sub-projects
"""
    }

    def "test running on declarative dcl"() {
        given: "a build-logic build registering an ecosystem plugin defining several software types"
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << """
            package com.example.restricted;

            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public abstract interface LibraryExtension {
                @Restricted
                Property<String> getName();
            }
        """
        file("build-logic/src/main/java/com/example/restricted/ApplicationExtension.java") << """
            package com.example.restricted;

            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public abstract interface ApplicationExtension {
                @Restricted
                Property<String> getName();
            }
        """
        file("build-logic/src/main/java/com/example/restricted/UtilityExtension.java") << """
            package com.example.restricted;

            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public abstract interface UtilityExtension {
                @Restricted
                Property<String> getName();
            }
        """
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "library", modelPublicType = LibraryExtension.class)
                public abstract LibraryExtension getLibrary();

                @SoftwareType(name = "application", modelPublicType = ApplicationExtension.class)
                public abstract ApplicationExtension getApplication();

                @SoftwareType(name = "utility", modelPublicType = UtilityExtension.class)
                public abstract UtilityExtension getUtility();

                @Override
                public void apply(Project project) {}
            }
        """
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
            import ${ RegistersSoftwareTypes.class.name};

            @RegistersSoftwareTypes({ RestrictedPlugin.class })
            abstract public class SoftwareTypeRegistrationPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) {}
            }
        """
        file("build-logic/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }

            ${mavenCentralRepository()}

            gradlePlugin {
                plugins {
                    create("restrictedPlugin") {
                        id = "com.example.restricted"
                        implementationClass = "com.example.restricted.RestrictedPlugin"
                    }
                    create("softwareTypeRegistrator") {
                        id = "com.example.restricted.ecosystem"
                        implementationClass = "com.example.restricted.SoftwareTypeRegistrationPlugin"
                    }
                }
            }
        """

        and: "a build that applies that ecosystem plugin to a multi-project build"
        settingsFile << """
            pluginManagement {
                includeBuild("build-logic")
            }

            plugins {
                id("com.example.restricted.ecosystem")
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = 'example'
            enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

            include("lib")
            include("app")
            include("util")
        """

        file("lib/build.gradle.dcl") << """
            library {
                name = "my-lib"
            }
        """
        file("app/build.gradle.dcl") << """
            application {
                name = "my-app"
            }
        """
        file("util/build.gradle.dcl") << """
            utility {
                name = "my-util"
            }
        """

        expect:
        succeeds("projects")
    }
}
