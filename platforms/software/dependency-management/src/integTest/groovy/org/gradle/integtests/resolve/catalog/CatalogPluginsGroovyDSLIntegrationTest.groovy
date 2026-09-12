/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.catalog

import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

class CatalogPluginsGroovyDSLIntegrationTest extends AbstractVersionCatalogIntegrationTest {

    private static final String PLUGIN_ID = 'com.acme.greeter'
    private static final String PLUGIN_VERSION = '1.5'
    private static final String TASK_NAME = 'greet'
    private static final String MESSAGE = 'Hello from plugin!'

    @Rule
    final MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    private publishGreeter(String version = PLUGIN_VERSION, String message = MESSAGE) {
        new PluginBuilder(file("greeter-$version"))
            .addPluginWithPrintlnTask(TASK_NAME, message, PLUGIN_ID)
            .publishAs("some", "artifact$version", version, pluginPortal, executer)
    }

    private void publishGreeterVersions(Map<String, String> messagesByVersion) {
        messagesByVersion.each { version, message -> publishGreeter(version, message).allowAll() }
        pluginPortal.getModuleMetaData(PLUGIN_ID, PLUGIN_ID + ".gradle.plugin").allowGetOrHead()
    }

    private void catalogWith(String declaration) {
        file("settings.gradle") << """
        dependencyResolutionManagement {
            versionCatalogs {
                libs {
                    plugin('greeter', '$PLUGIN_ID').$declaration
                }
            }
        }"""
    }

    private void applyGreeter(String path = "build.gradle") {
        file(path) << """
            plugins {
                alias(libs.plugins.greeter)
            }
        """
    }

    def "can apply a plugin declared in a catalog"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            plugin('$alias', 'com.acme.greeter').version('1.5')
        }
    }
}"""

        buildFile << """
            plugins {
                alias(libs.plugins.${alias.replace('-', '.')})
            }
        """

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains message

        where:
        alias << ['greeter', 'some.greeter', 'some-greeter']
    }

    def "can apply a plugin declared in a catalog in a TOML file"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        file("gradle/libs.versions.toml") << """
            [plugins]
            ${alias.replace('.', '-')} = "$pluginId:${pluginVersion}"
        """

        buildFile << """
            plugins {
                alias(libs.plugins.${alias.replace('-', '.')})
            }
        """

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains message

        where:
        alias << ['greeter', 'some.greeter', 'some-greeter']
    }

    def "can override version of a plugin declared in a catalog"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            plugin('greeter', 'com.acme.greeter').version('1.4')
        }
    }
}"""

        buildFile << """
            plugins {
                alias(libs.plugins.greeter).version("1.5")
            }
        """

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains message
    }

    def "can declare a plugin using a version declared in a catalog"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            version('greeter', '1.5')
        }
    }
}"""

        buildFile << """
            plugins {
                id("com.acme.greeter").version(libs.versions.greeter)
            }
        """

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains message
    }

    def "can apply a plugin alias that has sub-accessors"() {
        String pluginVersion = '1.5'
        String firstLevelTask = 'greet'
        String firstLevelPluginId = 'com.acme.greeter'
        String secondLevelPluginTask = 'greet-second'
        String secondLevelPluginId = 'com.acme.greeter.second'
        new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(firstLevelTask, 'Hello from first plugin!', firstLevelPluginId, "FirstPlugin")
            .addPluginWithPrintlnTask(secondLevelPluginTask, 'Hello from second plugin!', secondLevelPluginId, "SecondPlugin")
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)
            .allowAll()

        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            plugin('greeter', '$firstLevelPluginId').version('$pluginVersion')
            plugin('greeter-second', '$secondLevelPluginId').version('$pluginVersion')
        }
    }
}"""

        buildFile << """
            plugins {
                alias(libs.plugins.greeter)
                alias(libs.plugins.greeter.second)
            }
        """

        when:
        succeeds(firstLevelTask, secondLevelPluginTask)

        then:
        outputContains 'Hello from first plugin!'
        outputContains 'Hello from second plugin!'
    }

    def "can apply a plugin via buildscript and also sub-accessor plugin"() {
        String pluginVersion = '1.5'
        String firstPluginId = 'com.acme.greeter'
        new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask('greet', 'Hello from first plugin!', firstPluginId, "FirstPlugin")
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)
            .allowAll()
        String secondPluginId = 'com.acme.greeter2'
        new PluginBuilder(file("greeter-second"))
            .addPluginWithPrintlnTask('greet2', 'Hello from second plugin!', secondPluginId, "SecondPlugin")
            .publishAs("some", "artifact2", pluginVersion, pluginPortal, executer)
            .allowAll()

        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            library('$alias', 'some', 'artifact').version('1.5')
            library('$alias-second', 'some', 'artifact2').version('1.5')
        }
    }
}"""
        buildFile << """
            buildscript {
                repositories {
                    maven {
                        url = "${pluginPortal.uri}"
                    }
                }
                dependencies {
                    classpath(libs.${alias.replace('-', '.')})
                    classpath(libs.${alias.replace('-', '.')}.second)
                }
            }

            apply plugin: org.gradle.test.FirstPlugin
            apply plugin: org.gradle.test.SecondPlugin
        """

        when:
        succeeds('greet', 'greet2')

        then:
        outputContains 'Hello from first plugin!'
        outputContains 'Hello from second plugin!'

        where:
        alias << ['greeter', 'some.greeter', 'some-greeter']
    }

    def "resolves a plugin declared with a rich version [#declaration]"() {

        given:
        def plugin = publishGreeter()
        catalogWith(declaration)
        applyGreeter()

        when:
        plugin.allowAll()
        succeeds(TASK_NAME)

        then:
        outputContains MESSAGE

        where:
        declaration << [
            "version { prefer '$PLUGIN_VERSION' }",
            "version { strictly '$PLUGIN_VERSION' }",
            "version { require '$PLUGIN_VERSION'; prefer '$PLUGIN_VERSION' }",
            "version { require '$PLUGIN_VERSION'; reject '1.4' }",
            "version { require '$PLUGIN_VERSION'; branch = 'main' }",
        ]
    }

    def "a preferred version does not clash with a plugin already on the classpath"() {
        given:
        def plugin = publishGreeter()
        catalogWith("version { prefer '1.4' }")
        file("settings.gradle") << "\ninclude 'sub'"
        buildFile << """
            plugins {
                id '$PLUGIN_ID' version '$PLUGIN_VERSION' apply false
            }
        """
        applyGreeter("sub/build.gradle")

        when:
        plugin.allowAll()
        succeeds(":sub:$TASK_NAME")

        then:
        outputContains MESSAGE
    }

    def "a plugin alias without a version is not reported with an empty version"() {
        given:
        file("gradle/libs.versions.toml") << """
            [plugins]
            greeter = { id = "$PLUGIN_ID" }
        """
        applyGreeter()

        when:
        fails 'help'

        then:
        failure.assertHasDescription("Plugin [id: '$PLUGIN_ID'] was not found")
    }

    def "a preferred version is honoured inside the range the catalog requires"() {
        given:
        publishGreeterVersions(['1.4': 'Greetings from 1.4', '1.5': 'Greetings from 1.5', '1.6': 'Greetings from 1.6'])
        catalogWith("version { require '[1.0,2.0)'; prefer '1.5' }")
        applyGreeter()

        when:
        succeeds(TASK_NAME)

        then:
        outputContains 'Greetings from 1.5'
    }

    def "a rejected version is not selected from the range the catalog requires"() {
        given:
        publishGreeterVersions(['1.4': 'Greetings from 1.4', '1.5': 'Greetings from 1.5', '1.6': 'Greetings from 1.6'])
        catalogWith("version { require '[1.0,2.0)'; reject '1.6' }")
        applyGreeter()

        when:
        succeeds(TASK_NAME)

        then:
        outputContains 'Greetings from 1.5'
    }
}
