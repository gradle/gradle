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
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.internal.declarativedsl.settings.ProjectTypeFixture
import org.gradle.util.internal.TextUtil

/**
 * Integration tests for the `:projects` task, which reports the project structure and project types.
 * <p>
 * This test suite covers various scenarios including:
 * <ul>
 *   <li>Multi-project builds with composite builds</li>
 *   <li>Transitive composites</li>
 *   <li>Non-standard project directories</li>
 *   <li>Long project descriptions</li>
 *   <li>Project types registered via declarative DSL</li>
 * </ul>
 */
class ProjectReportTaskIntegrationTest extends AbstractIntegrationSpec implements ProjectTypeFixture {
    def "reports project structure with single composite"() {
        given:
        createDirs("p1", "p2", "p2/p22", "another")
        file("settings.gradle") << """rootProject.name = 'my-root-project'
include('p1')
include('p2')
include('p2:p22')
includeBuild('another')"""
        file('another/settings.gradle').touch()

        buildFile """description = 'This is a test project'"""
        groovyFile(file('p1/build.gradle'), """description = 'Initial/core project'""")
        groovyFile(file('p2/build.gradle'), """description = 'The second feature project'""")

        when:
        run ":projects"

        then:
        TextUtil.normaliseFileSeparators(output).contains(TextUtil.normaliseFileSeparators("""
Projects:

------------------------------------------------------------
Root project 'my-root-project'
------------------------------------------------------------

Location: ${buildFile.parentFile.path}
Description: This is a test project

Project hierarchy:

Root project 'my-root-project'
+--- Project ':p1' - Initial/core project
\\--- Project ':p2' - The second feature project
     \\--- Project ':p2:p22'

Project locations:

project ':p1' - /p1
project ':p2' - /p2
project ':p2:p22' - /p2/p22

Included builds:

\\--- Included build ':another'
"""))
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
Projects:

------------------------------------------------------------
Root project 'my-root-project'
------------------------------------------------------------

Location: ${buildFile.parentFile.path}

Project hierarchy:

Root project 'my-root-project'
No sub-projects

Included builds:

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

    def "rendering long project descriptions is done in root project"() {
        settingsFile << "rootProject.name = 'my-root-project'"
        buildFile << """
            description = '''
this is a long description
that spans
several lines
            '''
        """
        when:
        run "projects"
        then:
        outputContains """
Projects:

------------------------------------------------------------
Root project 'my-root-project'
------------------------------------------------------------

Location: ${buildFile.parentFile.path}
Description: this is a long description
that spans
several lines

Project hierarchy:

Root project 'my-root-project'
No sub-projects
"""
    }

    def "rendering long project descriptions is not done in child projects"() {
        settingsFile << """
rootProject.name = 'my-root-project'

include 'subA', ':subA:subB'
"""
        buildFile << """
            description = '''
this is a long description
that spans
several lines
            '''
        """

        file("subA/build.gradle") << """
            description = '''
this is another quite long description that should be truncated and it also
spans several lines
just like the root project description
            '''
        """

        file("subA/subB/build.gradle") << """
        description = '''I only have a short description'''
        """

        when:
        run "projects"
        then:
        outputContains """
Projects:

------------------------------------------------------------
Root project 'my-root-project'
------------------------------------------------------------

Location: ${buildFile.parentFile.path}
Description: this is a long description
that spans
several lines

Project hierarchy:

Root project 'my-root-project'
\\--- Project ':subA' - this is another quite long description that should be truncated and it also...
     \\--- Project ':subA:subB' - I only have a short description
"""
    }

    @ToBeFixedForIsolatedProjects(because = "Accesses project.description for another project")
    def "project project structure and project types for multi-project build using declarative dcl"() {
        given: "a build-logic build registering an ecosystem plugin defining several project types via several plugins"
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
        file("build-logic/src/main/java/com/example/restricted/LibraryPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${SoftwareType.class.name};

            public abstract class LibraryPlugin implements Plugin<Project> {
                @SoftwareType(name = "library", modelPublicType = LibraryExtension.class)
                public abstract LibraryExtension getLibrary();

                @Override
                public void apply(Project project) { }
            }
        """
        file("build-logic/src/main/java/com/example/restricted/ApplicationPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${SoftwareType.class.name};

            public abstract class ApplicationPlugin implements Plugin<Project> {
                @SoftwareType(name = "application", modelPublicType = ApplicationExtension.class)
                public abstract ApplicationExtension getApplication();

                @Override
                public void apply(Project project) { }
            }
        """
        file("build-logic/src/main/java/com/example/restricted/UtilityPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${SoftwareType.class.name};

            public abstract class UtilityPlugin implements Plugin<Project> {
                @SoftwareType(name = "utility", modelPublicType = UtilityExtension.class)
                public abstract UtilityExtension getUtility();

                @Override
                public void apply(Project project) { }
            }
        """
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
            import ${ RegistersSoftwareTypes.class.name};

            @RegistersSoftwareTypes({ LibraryPlugin.class, ApplicationPlugin.class, UtilityPlugin.class })
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
                    create("softwareTypeRegistrator") {
                        id = "com.example.restricted.ecosystem"
                        implementationClass = "com.example.restricted.SoftwareTypeRegistrationPlugin"
                    }
                }
            }
        """

        and: "a build that applies that ecosystem plugin to a multi-project build, with each project using a different project type"
        settingsFile << """
            pluginManagement {
                includeBuild("build-logic")
            }

            plugins {
                id("com.example.restricted.ecosystem")
            }

            rootProject.name = 'example'

            include("lib")
            include("app")
            include("util")
        """
        buildFile << """
            project(":lib") {
                description = "Sample library project"
            }
            project(":util") {
                description = "Utilities and common code"
            }
            project(":app") {
                description = "Sample application project"
            }
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

        outputContains("""

Available project types:

application (com.example.restricted.ApplicationExtension)
        Defined in: com.example.restricted.ApplicationPlugin
        Registered by: com.example.restricted.SoftwareTypeRegistrationPlugin
library (com.example.restricted.LibraryExtension)
        Defined in: com.example.restricted.LibraryPlugin
        Registered by: com.example.restricted.SoftwareTypeRegistrationPlugin
utility (com.example.restricted.UtilityExtension)
        Defined in: com.example.restricted.UtilityPlugin
        Registered by: com.example.restricted.SoftwareTypeRegistrationPlugin

Projects:

------------------------------------------------------------
Root project 'example'
------------------------------------------------------------

Location: ${buildFile.parentFile.path}

Project hierarchy:

Root project 'example'
+--- Project ':app' (application) - Sample application project
+--- Project ':lib' (library) - Sample library project
\\--- Project ':util' (utility) - Utilities and common code
""")
    }

    def "reports project structure with non-standard project directories"() {
        given:
        createDirs("features", "core/logic", "core/transport", "server")
        file("settings.gradle") << """
            rootProject.name = 'my-root-project'

            include(':featureX')
            project(':featureX').projectDir = file('features/featureX')

            include(":featureY")
            project(':featureY').projectDir = file('features/featureY')

            include(':common')
            project(':common').projectDir = file('core/logic/common')

            include(':transport')
            project(':transport').projectDir = file('core/transport')

            includeBuild('server')
        """
        file('server/settings.gradle').touch()

        buildFile """description = 'This is a test project'"""
        groovyFile(file('features/featureX/build.gradle'), """description = 'A standard feature'""")
        groovyFile(file('features/featureY/build.gradle'),
            """description = '''A more experimental feature that is not yet fully ready for production.
                    Team Alpha is still actively developing this.
                    Requires the foo module be preinstalled.'''""")
        groovyFile(file('core/logic/common/build.gradle'), """description = 'Common logic shared across features'""")
        groovyFile(file('core/transport/build.gradle'), """description = 'Transport layer for communication'""")

        when:
        run ":projects"

        then:
        TextUtil.normaliseFileSeparators(output).contains(TextUtil.normaliseFileSeparators("""
Projects:

------------------------------------------------------------
Root project 'my-root-project'
------------------------------------------------------------

Location: ${buildFile.parentFile.path}
Description: This is a test project

Project hierarchy:

Root project 'my-root-project'
+--- Project ':common' - Common logic shared across features
+--- Project ':featureX' - A standard feature
+--- Project ':featureY' - A more experimental feature that is not yet fully ready for production....
\\--- Project ':transport' - Transport layer for communication

Project locations:

project ':common' - /core/logic/common
project ':featureX' - /features/featureX
project ':featureY' - /features/featureY
project ':transport' - /core/transport

Included builds:

\\--- Included build ':server'

To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :common:tasks
"""))
    }

    def "renders help message"() {
        settingsFile << "rootProject.name = 'my-root-project'"

        when:
        run "projects"
        then:
        outputContains """
To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :tasks
"""
    }
}
