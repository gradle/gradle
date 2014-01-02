/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.plugin.resolve.internal.AndroidPluginMapper
import org.gradle.test.fixtures.bintray.BintrayApi
import org.gradle.test.fixtures.bintray.BintrayTestServer
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.junit.Rule

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class PluginHandlerScriptIntegTest extends AbstractIntegrationSpec {

    private static final String SCRIPT = "println 'out'; plugins { println 'in' }"

    @Rule BintrayTestServer bintray = new BintrayTestServer(executer, mavenRepo) // provides a double for JCenter
    def pluginBuilder = new PluginBuilder(executer, file("plugin"))

    def pluginMessage = "from plugin"
    def pluginTaskName = "pluginTask"
    def pluginVersion = "1.0"

    def "build scripts have plugin blocks"() {
        when:
        buildFile << SCRIPT
        buildFile << """
            plugins {
              apply plugin: 'java'
            }
        """

        then:
        executesCorrectly()
        output.contains "javadoc" // task added by java plugin
    }

    def "settings scripts have plugin blocks"() {
        when:
        settingsFile << SCRIPT

        then:
        executesCorrectly()
    }

    def "init scripts have plugin blocks"() {
        def initScript = file("init.gradle")

        when:
        initScript << SCRIPT

        then:
        args "-I", initScript.absolutePath
        executesCorrectly()
    }

    def "cannot use plugin block when script target is not plugin capable"() {
        buildFile << """
            task foo {}
            apply {
                from "plugin.gradle"
                to foo
            }
        """

        file("plugin.gradle") << """
            plugins {
                apply plugin: "foo"
            }
        """

        when:
        fails "foo"

        then:
        errorOutput.contains("cannot have plugins applied to it")
    }


    def void executesCorrectly() {
        succeeds "tasks"
        assert output.contains(toPlatformLineSeparators("in\nout\n")) // Testing the the plugins {} block is extracted and executed before the “main” content
    }

    void "plugins block has no implicit access to owner context"() {
        when:
        buildScript """
            plugins {
                owner.buildscript {} // works
                try {
                    buildscript {}
                } catch(MissingMethodException e) {
                    // ok
                }
                println "end-of-plugins"
            }
        """

        then:
        succeeds "tasks"
        and:
        output.contains("end-of-plugins")
    }

    void "can resolve android plugin"() {
        given:
        bintray.start()

        // Not expecting a search of the bintray API, as there is an explicit mapper for this guy
        publishPluginToBintray(AndroidPluginMapper.ID, AndroidPluginMapper.GROUP, AndroidPluginMapper.NAME)

        buildScript """
          plugins {
            apply plugin: "$AndroidPluginMapper.ID", version: $pluginVersion
          }
        """

        when:
        succeeds pluginTaskName

        then:
        output.contains pluginMessage
    }

    def "android plugin requires version"() {
        given:
        buildScript """
          plugins {
            apply plugin: "$AndroidPluginMapper.ID"
          }
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("The 'android' plugin requires a version")
    }

    def void publishPluginToBintray(String id, String group, String name, String version = pluginVersion) {
        def module = bintray.jcenter.module(group, name, version)
        module.allowAll()
        def artifact = module.artifact([:])
        module.publish()
        pluginBuilder.addPluginWithPrintlnTask(pluginTaskName, pluginMessage, id)
        pluginBuilder.publishTo(artifact.file)
    }

    void "can use plugin classes in script"() {
        given:
        bintray.start()

        pluginBuilder.groovy("EchoTask.groovy") << """
            package $pluginBuilder.packageName

            class EchoTask extends ${DefaultTask.name} {
                @${TaskAction.name}
                void doEcho() {
                    println "$pluginMessage"
                }
            }
        """

        publishPluginToBintray("test", "test", "test")
        bintray.api.expectPackageSearch("test", new BintrayApi.FoundPackage("foo", "test:test"))

        buildScript """
          plugins {
            apply plugin: "test", version: "$pluginVersion"
          }

          task echo(type: ${pluginBuilder.packageName}.EchoTask) {}
        """

        when:
        succeeds "echo"

        then:
        output.contains pluginMessage
    }

    void "core plugins cannot have a version number"() {
        given:
        buildScript """
            plugins {
                apply plugin: "java", version: "1.0"
            }
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Core plugins cannot have a version number. They are versioned with Gradle itself.")
    }

    void "plugins block does not leak into build script proper"() {
        given:
        buildFile << """
            configurations {
                plugins {
                    transitive = false // just use some configuration specific API here
                }
            }

            task showConfigurationsSize << {
                println "configurations: " + configurations.size()
                println "plugins transitive: " + configurations.plugins.transitive
            }
        """

        when:
        succeeds "sCS"

        then:
        output.contains("configurations: 1")
        output.contains("plugins transitive: false")
    }
}
