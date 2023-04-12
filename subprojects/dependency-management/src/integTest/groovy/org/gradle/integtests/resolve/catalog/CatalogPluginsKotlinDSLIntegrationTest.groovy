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
import org.gradle.test.fixtures.file.LeaksFileHandles
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
 *
 * These tests use Groovy settings files because the parent class sets up things in it already.
 */
@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class CatalogPluginsKotlinDSLIntegrationTest extends AbstractVersionCatalogIntegrationTest implements PluginDslSupport {
    @Rule
    final MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def "can declare multiple catalogs"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        plugin("$alias", "com.acme.greeter").version("1.5")
                    }
                    create("otherLibs") {
                        plugin("$alias", "com.acme.greeter").version("1.5")
                    }
                }
            }
        """
        buildFile.renameTo(file('fixture.gradle'))
        buildKotlinFile << """
            plugins {
                alias(otherLibs.plugins.${alias.replace('-', '.')})
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

        file("settings.gradle") << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        plugin('greeter', 'com.acme.greeter').version('1.4')
                    }
                }
            }
        """
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

        file("settings.gradle") << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version('greeter', '1.5')
                    }
                }
            }
        """
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

        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        plugin("greeter", "$firstLevelPluginId").version("$pluginVersion")
                        plugin("greeter-second", "$secondLevelPluginId").version("$pluginVersion")
                    }
                }
            }
        """
        buildFile.renameTo(file('fixture.gradle'))
        buildKotlinFile << """
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
                        library('greeter', 'some', 'artifact').version('1.5')
                        library('greeter-second', 'some', 'artifact2').version('1.5')
                    }
                }
            }
        """
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
                    classpath(libs.greeter.second)
                }
            }
            apply<org.gradle.test.FirstPlugin>()
            apply<org.gradle.test.SecondPlugin>()
        """

        when:
        succeeds('greet', 'greet2')

        then:
        outputContains 'Hello from first plugin!'
        outputContains 'Hello from second plugin!'
    }

    def "emits deprecation warning when #useCase from plugins block"() {

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
                        version("greeter", "$pluginVersion")
                        plugin("greeter", "$pluginId").versionRef("greeter")
                        library("greeter", "some", "artifact").versionRef("greeter")
                        library("sub-greeter", "some", "artifact").versionRef("greeter")
                        bundle("greeter", ["greeter"])
                        bundle("sub-greeter", ["greeter"])
                    }
                }
            }
        """
        buildFile.renameTo(file('fixture.gradle'))
        buildKotlinFile << """
            plugins {
                $pluginRequest
            }
            apply(from="fixture.gradle")
        """

        when:
        plugin.allowAll()
        executer.expectDocumentedDeprecationWarning(
            "Accessing libraries or bundles from version catalogs in the plugins block. " +
                "This behavior has been deprecated. " +
                "This behavior is scheduled to be removed in Gradle 9.0. " +
                "Only use versions or plugins from catalogs in the plugins block. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#kotlin_dsl_deprecated_catalogs_plugins_block"
        )
        succeeds taskName

        then:
        outputContains message

        where:
        useCase               | pluginRequest
        'using libraries'     | 'id("com.acme.greeter") version libs.greeter.get().version'
        'using sub libraries' | 'id("com.acme.greeter") version libs.sub.greeter.get().version'
        'using bundles'       | 'id("com.acme.greeter") version libs.bundles.greeter.get().first().version'
        'using sub bundles'   | 'id("com.acme.greeter") version libs.bundles.sub.greeter.get().first().version'
    }
}
