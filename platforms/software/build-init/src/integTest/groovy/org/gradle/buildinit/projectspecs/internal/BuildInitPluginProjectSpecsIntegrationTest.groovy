/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.buildinit.projectspecs.internal

import org.gradle.api.JavaVersion
import org.gradle.buildinit.plugins.AbstractInitIntegrationSpec
import org.gradle.buildinit.plugins.TestsInitProjectSpecsViaPlugin
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class BuildInitPluginProjectSpecsIntegrationTest extends AbstractInitIntegrationSpec implements TestsInitProjectSpecsViaPlugin, JavaToolchainFixture {
    private static final String ARBITRARY_PLUGIN_ID = "org.barfuin.gradle.taskinfo"
    private static final String ARBITRARY_PLUGIN_VERSION = "2.2.0"
    private static final String ARBITRARY_PLUGIN_SPEC = "$ARBITRARY_PLUGIN_ID:$ARBITRARY_PLUGIN_VERSION"

    def "can specify 3rd party plugin using argument to init"() {
        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
    }

    def "can specify plugin using argument to init with root build file present"() {
        groovyFile("new-project/build.gradle", """
            plugins {
                id 'java-library'
            }
        """)

        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
    }

    def "can specify plugin using argument to init with root KTS build file present"() {
        kotlinFile("new-project/build.gradle.kts", """
            plugins {
                `java-library`
            }
        """)

        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
    }

    def "can specify plugin using argument to init with settings file present"() {
        groovyFile("new-project/settings.gradle","""
            rootProject.name = "rootProject"
        """)

        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
    }

    def "can specify plugin using argument to init with settings KTS file present"() {
        kotlinFile("new-project/settings.gradle.kts","""
            rootProject.name = "rootProject"
        """)

        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
    }

    def "can specify plugin using argument to init with root build and settings files present"() {
        groovyFile("new-project/settings.gradle","""
            rootProject.name = "rootProject"
        """)

        groovyFile("new-project/build.gradle", """
            plugins {
                id 'java-library'
            }
        """)

        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
    }

    def "can specify plugin using argument to init with root build and settings KTS files present"() {
        kotlinFile("new-project/settings.gradle.kts","""
            rootProject.name = "rootProject"
        """)

        kotlinFile("new-project/build.gradle.kts", """
            plugins {
                `java-library`
            }
        """)

        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
    }

    def "can specify plugin using argument to init with root build and settings files present in multiproject build"() {
        groovyFile("new-project/settings.gradle", """
            rootProject.name = "rootProject"
            include("subproject")
        """)

        groovyFile("new-project/build.gradle", """
            plugins {
                id 'java-library'
            }
        """)

        groovyFile("new-project/subproject/build.gradle",
            """
                plugins {
                    id 'java-library'
                }
            """
        )

        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
        // TODO: should appear exactly once, but no way to automatically verify this currently.  Looking at the output, it is true currently
    }

    def "can specify plugin using argument to init with root build and settings KTS files present in multiproject build"() {
        kotlinFile("new-project/settings.gradle.kts", """
            rootProject.name = "rootProject"
            include("subproject")
        """)

        kotlinFile("new-project/build.gradle.kts", """
            plugins {
                `java-library`
            }
        """)

        kotlinFile("new-project/subproject/build.gradle.kts",
            """
                plugins {
                    `java-library`
                }
            """
        )

        when:
        initSucceedsWithPluginSupplyingSpec(ARBITRARY_PLUGIN_SPEC)

        then:
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
        // TODO: should appear exactly once, but no way to automatically verify this currently.  Looking at the output, it is true currently
    }

    @LeaksFileHandles
    def "can generate custom project type using argument to init"() {
        given:
        publishTestPlugin()

        when:
        initSucceedsWithPluginSupplyingSpec("org.example.myplugin:1.0")

        then:
        assertResolvedPlugin("org.example.myplugin", "1.0")
        outputDoesNotContain("MyPlugin applied.")
        assertLoadedSpec("First Project Type")
        assertLoadedSpec("Second Project Type")

        // Note: If running in non-interactive mode, first template is automatically used
        assertProjectFileGenerated("project.output", "MyGenerator created this First Project Type project.")
        assertWrapperGenerated()
    }

    @LeaksFileHandles
    def "can generate custom project type when specifying multiple custom plugins using arguments to init"() {
        given:
        publishTestPlugin()

        when:
        initSucceedsWithPluginSupplyingSpec("org.example.myplugin:1.0,$ARBITRARY_PLUGIN_SPEC")

        then:
        assertResolvedPlugin("org.example.myplugin", "1.0")
        assertResolvedPlugin(ARBITRARY_PLUGIN_ID, ARBITRARY_PLUGIN_VERSION)
        outputDoesNotContain("MyPlugin applied.")
        assertLoadedSpec("First Project Type")
        assertLoadedSpec("Second Project Type")

        // Note: If running in non-interactive mode, first template is used
        assertProjectFileGenerated("project.output", "MyGenerator created this First Project Type project.")
        assertWrapperGenerated()
    }

    @LeaksFileHandles
    def "can generate a custom project type non-interactively using --type if multiple types are available using argument to init"() {
        given:
        publishTestPlugin()

        when:
        initSucceedsWithPluginSupplyingSpec("org.example.myplugin:1.0", "second-project-type")

        then:
        assertResolvedPlugin("org.example.myplugin", "1.0")
        outputDoesNotContain("MyPlugin applied.")
        assertLoadedSpec("First Project Type")
        assertLoadedSpec("Second Project Type")

        // Note: If running in non-interactive mode, first template is used
        assertProjectFileGenerated("project.output", "MyGenerator created this Second Project Type project.")
        assertWrapperGenerated()
    }

    @LeaksFileHandles
    @Requires(UnitTestPreconditions.Jdk17OrLater) // D-G produces a project that requires Java 21
    def "can generate declarative project type using argument to init"() {
        when:
        executer.withJvm(AvailableJavaHomes.getJdk21())
        initSucceedsWithPluginSupplyingSpec("org.gradle.experimental.jvm-ecosystem:0.1.13")

        then:
        assertResolvedPlugin("org.gradle.experimental.jvm-ecosystem", "0.1.13")
        assertLoadedSpec("Declarative Java Application Project")

        // Smoke test 2 DCL files
        assertProjectFileGenerated("settings.gradle.dcl", """pluginManagement {
    repositories {
        google() // Needed for the Android plugin, applied by the unified plugin
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.experimental.jvm-ecosystem") version "0.1.13"
}

rootProject.name = "example-java-app"

include("app")
include("list")
include("utilities")

defaults {
    javaLibrary {
        javaVersion = 17

        dependencies {
            implementation("org.apache.commons:commons-text:1.11.0")
        }

        testing {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
    }

    javaApplication {
        javaVersion = 17

        dependencies {
            implementation("org.apache.commons:commons-text:1.11.0")
        }

        testing {
            testJavaVersion = 21
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
    }
}
""")
        assertProjectFileGenerated("app/build.gradle.dcl", """javaApplication {
    mainClass = "org.example.app.App"

    dependencies {
        implementation("org.apache.commons:commons-text:1.11.0")
        implementation(project(":utilities"))
    }
}
""")

        assertWrapperGenerated()

        when:
        withInstallations(Jvm.current(), AvailableJavaHomes.getJdk(JavaVersion.VERSION_17))

        then:
        canBuildGeneratedProject(AvailableJavaHomes.getJdk21())
    }

    private void initSucceedsWithPluginSupplyingSpec(String pluginsProp = null, String type = null) {
        targetDir = file("new-project").with { createDir() }

        def args = ["init"]
        if (pluginsProp) {
            args << "-D${AutoAppliedPluginHandler.INIT_PROJECT_SPEC_SUPPLIERS_PROP}=$pluginsProp".toString()
        }
        if (type) {
            args << "--type" << type
        }
        args << "--overwrite"
        args << "--info"
        args << "--init-script" << "../init.gradle"

        println "Executing: '${args.join(" ")}'"
        println "Working Dir: '$targetDir'"

        executer.noDeprecationChecks() // TODO: We don't care about these here, they are from the declarative-prototype build, remove when depending upon published version and not included build
        succeeds args
    }

    @Override
    String subprojectName() {
        return "new-project"
    }
}
