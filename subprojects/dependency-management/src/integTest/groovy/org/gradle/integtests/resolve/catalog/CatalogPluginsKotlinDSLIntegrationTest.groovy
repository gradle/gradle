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


import org.gradle.integtests.resolve.PluginDslSupport
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule
/**
 * Mirror test of {@link CatalogPluginsGroovyDSLIntegrationTest}.
 *
 * This test isn't meant to check the behavior of the extension generation like the other
 * integration tests in this package, but only what is very specific to the Kotlin DSL.
 * Because it requires the generated Gradle API it runs significantly slower than the other
 * tests so avoid adding tests here if they cannot be expressed with the Groovy DSL.
 */
class CatalogPluginsKotlinDSLIntegrationTest extends AbstractVersionCatalogIntegrationTest implements PluginDslSupport {
    @Rule
    final MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def setup() {
        usePluginRepoMirror = false // otherwise the plugin portal fixture doesn't work!
    }

    def "can apply a plugin declared in a catalog"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        // We use the Groovy DSL for settings because that's not what we want to
        // test and the setup would be more complicated with Kotlin
        settingsFile << """
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            alias("$alias").toPluginId("com.acme.greeter").version("1.5")
        }
    }
}"""
        buildFile.renameTo(file('fixture.gradle'))
        buildKotlinFile << """
            plugins {
                alias(libs.plugins.${alias.replace('-', '.')})
            }

            apply(from="fixture.gradle")
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

        // We use the Groovy DSL for settings because that's not what we want to
        // test and the setup would be more complicated with Kotlin
        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            alias('greeter').toPluginId('com.acme.greeter').version('1.4')
        }
    }
}"""
        buildFile.renameTo(file('fixture.gradle'))
        buildKotlinFile << """
            plugins {
                alias(libs.plugins.greeter) version "1.5"
            }

            apply(from="fixture.gradle")
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

        // We use the Groovy DSL for settings because that's not what we want to
        // test and the setup would be more complicated with Kotlin
        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            version('greeter', '1.5')
        }
    }
}"""
        buildFile.renameTo(file('fixture.gradle'))
        buildKotlinFile << """
            plugins {
                id("com.acme.greeter") version libs.versions.greeter
            }

            apply(from="fixture.gradle")
        """

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains message
    }

    def "can apply a plugin declared in a catalog via buildscript"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        // We use the Groovy DSL for settings because that's not what we want to
        // test and the setup would be more complicated with Kotlin
        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            alias('greeter').to('some', 'artifact').version('1.5')
        }
    }
}"""
        buildFile.renameTo(file('fixture.gradle'))
        buildKotlinFile << """
            buildscript {
                repositories {
                    maven {
                        url = uri("${pluginPortal.uri}")
                    }
                }
                dependencies {
                    classpath(libs.greeter)
                }
            }
            apply<org.gradle.test.TestPlugin>()
        """

        when:
        plugin.pluginModule.allowAll()
        succeeds taskName

        then:
        outputContains message

    }
}
