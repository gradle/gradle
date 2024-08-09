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

package org.gradle.buildinit.templates.internal

import org.gradle.buildinit.plugins.AbstractInitIntegrationSpec
import org.gradle.buildinit.plugins.TestsInitTemplatePlugin
import org.gradle.plugin.management.internal.template.TemplatePluginHandler
import org.gradle.test.fixtures.file.LeaksFileHandles

class BuildInitPluginTemplatesIntegrationTest extends AbstractInitIntegrationSpec implements TestsInitTemplatePlugin {
    def "can specify 3rd party plugin using argument to init"() {
        when:
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
    }

    def "can specify plugin using argument to init with root build file present"() {
        groovyFile("new-project/build.gradle", """
            plugins {
                id 'java-library'
            }
        """)

        when:
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
    }

    def "can specify plugin using argument to init with root KTS build file present"() {
        kotlinFile("new-project/build.gradle.kts", """
            plugins {
                `java-library`
            }
        """)

        when:
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
    }

    def "can specify plugin using argument to init with settings file present"() {
        groovyFile("new-project/settings.gradle","""
            rootProject.name = "rootProject"
        """)

        when:
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
    }

    def "can specify plugin using argument to init with settings KTS file present"() {
        kotlinFile("new-project/settings.gradle.kts","""
            rootProject.name = "rootProject"
        """)

        when:
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
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
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
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
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
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
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
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
        initSucceedsWithTemplatePlugins("org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
        // TODO: should appear exactly once, but no way to automatically verify this currently.  Looking at the output, it is true currently
    }

    @LeaksFileHandles
    def "can specify custom plugin using argument to init"() {
        given:
        publishTestPlugin()

        when:
        initSucceedsWithTemplatePlugins("org.example.myplugin:1.0")

        then:
        assertResolvedPlugin("org.example.myplugin", "1.0")
        outputDoesNotContain("MyPlugin applied.")
        assertLoadedTemplate("Custom Project Type")
        assertLoadedTemplate("Custom Project Type 2")

        // Note: If running in non-interactive mode, first template is used
        assertProjectFileGenerated("project.output", "MyGenerator created this Custom Project Type project.")
        assertWrapperGenerated()
    }

    @LeaksFileHandles
    def "can specify multiple plugins using argument to init"() {
        given:
        publishTestPlugin()

        when:
        initSucceedsWithTemplatePlugins("org.example.myplugin:1.0,org.barfuin.gradle.taskinfo:2.2.0")

        then:
        assertResolvedPlugin("org.example.myplugin", "1.0")
        assertResolvedPlugin("org.barfuin.gradle.taskinfo", "2.2.0")
        outputDoesNotContain("MyPlugin applied.")
        assertLoadedTemplate("Custom Project Type")
        assertLoadedTemplate("Custom Project Type 2")

        // Note: If running in non-interactive mode, first template is used
        assertProjectFileGenerated("project.output", "MyGenerator created this Custom Project Type project.")
        assertWrapperGenerated()
    }

    @LeaksFileHandles
    def "can specify declarative plugin using argument to init"() {
        when:
        initSucceedsWithTemplatePlugins("org.gradle.experimental.jvm-ecosystem:0.1.9")

        then:
        assertResolvedPlugin("org.gradle.experimental.jvm-ecosystem", "0.1.9")
        assertLoadedTemplate("Java Project Type")

        // Note: If running in non-interactive mode, first template is used
        assertProjectFileGenerated("project.output", "Hello, World!")
        assertWrapperGenerated()
    }

    private void initSucceedsWithTemplatePlugins(String pluginsProp = null) {
        def newProjectDir = file("new-project").with { createDir() }
        targetDir = newProjectDir

        def args = ["init"]
        if (pluginsProp) {
            args << "-D${TemplatePluginHandler.TEMPLATE_PLUGINS_PROP}=$pluginsProp".toString()
        }
        args << "--overwrite"
        args << "--info"
        args << "--init-script" << "../init.gradle"

        println "Executing: '${args.join(" ")}')"
        println "Working Dir: '$newProjectDir'"

        executer.noDeprecationChecks() // We don't care about these here
        succeeds args
    }

    @Override
    String subprojectName() {
        return "app"
    }
}
