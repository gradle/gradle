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
import org.gradle.test.fixtures.bintray.BintrayApi
import org.gradle.test.fixtures.bintray.BintrayTestServer
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.util.GradleVersion
import org.junit.Rule

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class PluginHandlerScriptIntegTest extends AbstractIntegrationSpec {

    private static final String SCRIPT = "plugins { println 'in' }; println 'out'"

    @Rule BintrayTestServer bintray = new BintrayTestServer(executer, mavenRepo) // provides a double for JCenter
    def pluginBuilder = new PluginBuilder(executer, file("plugin"))

    def pluginMessage = "from plugin"
    def pluginTaskName = "pluginTask"
    def pluginVersion = "1.0"

    def "build scripts have plugin blocks"() {
        when:
        buildFile << SCRIPT

        then:
        executesCorrectly()
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
        assert output.contains(toPlatformLineSeparators("in\nout\n"))
    }

    void "plugins block has no implicit access to owner context"() {
        when:
        buildScript """
            plugins {
                try {
                    buildscript {}
                } catch(MissingMethodException e) {
                    // ok
                }

                try {
                    version
                } catch(MissingPropertyException e) {
                    // ok
                }

                assert delegate == null
                assert this instanceof ${PluginHandler.name}
                assert owner == this

                println "end-of-plugins"
            }
        """

        then:
        succeeds "tasks"
        and:
        output.contains("end-of-plugins")
    }

    void "can resolve core plugins"() {
        when:
        buildScript """
            plugins {
              apply plugin: 'java'
            }
        """

        then:
        succeeds "javadoc"
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
        failure.assertHasCause("Plugin 'java' is a core Gradle plugin, which cannot be specified with a version number")
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

    def "buildscript blocks are allowed before plugin statements"() {
        when:
        buildScript """
            buildscript {}
            plugins {}
        """

        then:
        succeeds "tasks"
    }

    def "build logic cannot precede plugins block"() {
        when:
        buildScript """
            someThing()
            plugins {}
        """

        then:
        fails "tasks"

        and:
        failure.assertHasLineNumber 3
        errorOutput.contains "only buildscript {} and and other plugins {} script blocks are allowed before plugins {} blocks, no other statements are allowed"
    }

    def "build logic cannot precede any plugins block"() {
        when:
        buildScript """
            plugins {}
            someThing()
            plugins {}
        """

        then:
        fails "tasks"

        and:
        failure.assertHasLineNumber 4
        errorOutput.contains "only buildscript {} and and other plugins {} script blocks are allowed before plugins {} blocks, no other statements are allowed"
    }

    def "failed resolution provides helpful error message"() {
        given:
        bintray.start()

        buildScript """
            plugins {
                apply plugin: "foo"
            }
        """

        and:
        bintray.api.expectPackageSearch("foo")

        when:
        fails "tasks"

        then:
        errorOutput.contains """Cannot resolve plugin request [plugin: 'foo'] from plugin repositories:
   - Gradle Distribution Plugins (listing: http://gradle.org/docs/${GradleVersion.current().version}/userguide/standard_plugins.html)
   - Gradle Bintray Plugin Repository (listing: https://bintray.com/gradle-plugins-development/gradle-plugins)"""
    }

}
