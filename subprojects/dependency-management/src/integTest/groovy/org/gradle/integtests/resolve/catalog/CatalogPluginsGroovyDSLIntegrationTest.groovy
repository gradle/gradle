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

class CatalogPluginsGroovyDSLIntegrationTest extends AbstractVersionCatalogIntegrationTest implements PluginDslSupport {
    @Rule
    final MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)


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
        withPluginAlias "libs.plugins.${alias.replace('-', '.')}"

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
        withPluginAlias "libs.plugins.${alias.replace('-', '.')}"

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
        withPlugins([:], ["libs.plugins.greeter": '1.5'])

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
        withPluginsBlockContents(
            'id \'com.acme.greeter\' version libs.versions.greeter'
        )

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
        withPluginAliases(["libs.plugins.greeter", "libs.plugins.greeter.second"])

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
        buildFile.text = """
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
        """ + buildFile.text

        buildFile << """
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

}
