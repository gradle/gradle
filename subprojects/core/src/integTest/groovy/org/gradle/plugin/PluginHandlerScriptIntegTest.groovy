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
import spock.lang.Ignore

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class PluginHandlerScriptIntegTest extends AbstractIntegrationSpec {

    private static final String SCRIPT = "plugins { println 'in' }; println 'out'"

    @Rule BintrayTestServer bintray = new BintrayTestServer(executer, mavenRepo) // provides a double for JCenter

    int pluginCounter = 0

    def pluginMessage = "from plugin"
    def pluginTaskName = "pluginTask"
    def pluginVersion = "1.0"

    PluginBuilder pluginBuilder() {
        new PluginBuilder(executer, file("plugin${++pluginCounter}"))
    }

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

    def void publishPluginToBintray(PluginBuilder pluginBuilder, String group, String name, String version = pluginVersion) {
        def module = bintray.jcenter.module(group, name, version)
        module.allowAll()
        def artifact = module.artifact([:])
        module.publish()
        pluginBuilder.publishTo(artifact.file)
    }

    void "can use plugin classes in script"() {
        given:
        bintray.start()
        def pb = pluginBuilder()
        pb.groovy("EchoTask.groovy") << """
            package $pb.packageName

            class EchoTask extends ${DefaultTask.name} {
                @${TaskAction.name}
                void doEcho() {
                    println "$pluginMessage"
                }
            }
        """

        pb.addPlugin("", "test")
        publishPluginToBintray(pb, "test", "test")
        bintray.api.expectPackageSearch("test", new BintrayApi.FoundPackage("foo", "test:test"))

        buildScript """
          plugins {
            apply plugin: "test", version: "$pluginVersion"
          }

          task echo(type: ${pb.packageName}.EchoTask) {}
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

    def "buildscript blocks are not allowed after plugin blocks"() {
        when:
        buildScript """
            plugins {}
            buildscript {}
        """

        then:
        fails "tasks"

        and:
        failure.assertHasLineNumber 3
        errorOutput.contains("all buildscript {} blocks must appear before any plugins {} blocks")
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
        errorOutput.contains "Cannot resolve plugin request [plugin: 'foo'] from plugin repositories:"
        errorOutput.contains "- Gradle Distribution Plugins (listing: http://gradle.org/docs/${GradleVersion.current().version}/userguide/standard_plugins.html)"
        errorOutput.contains "- Gradle Bintray Plugin Repository (listing: https://bintray.com/gradle-plugins-development/gradle-plugins)"
    }

    private publishTestPlugin() {
        def pluginBuilder = new PluginBuilder(executer, testDirectory.file("plugin"))

        def module = mavenRepo.module("plugin", "plugin")
        def artifactFile = module.artifact([:]).artifactFile
        module.publish()

        def message = "from plugin"
        def taskName = "pluginTask"
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "plugin")
        pluginBuilder.publishTo(artifactFile)
    }

    private testPluginBuildscriptBlock() {
        return """
            buildscript {
                repositories {
                    maven { url "$mavenRepo.uri" }
                }
                dependencies {
                    classpath "plugin:plugin:1.0"
                }
            }
        """
    }

    private testPluginPluginsBlock() {
        return """
            plugins {
                apply plugin: "plugin", version: "1.0"
            }
        """
    }

    def "cannot apply plugins added to buildscript classpath in plugins block"() {
        given:
        publishTestPlugin()

        when:
        buildScript """
            ${testPluginBuildscriptBlock()}
            ${testPluginPluginsBlock()}
        """

        then:
        fails "tasks"

        and:
        errorOutput.contains "Plugin 'plugin' is already on the script classpath (plugins on the script classpath cannot be used in a plugins {} block; move \"apply plugin: 'plugin'\" outside of the plugins {} block)"
    }

    def "cannot apply plugins added to parent buildscript classpath in plugins block"() {
        given:
        publishTestPlugin()

        when:
        buildScript """
            ${testPluginBuildscriptBlock()}
        """

        settingsFile << "include 'sub'"

        file("sub/build.gradle") << """
            ${testPluginPluginsBlock()}
        """

        then:
        fails "sub:tasks"

        and:
        errorOutput.contains "Plugin 'plugin' is already on the script classpath (plugins on the script classpath cannot be used in a plugins {} block; move \"apply plugin: 'plugin'\" outside of the plugins {} block)"
    }

    def "plugin classes are reused across projects"() {
        when:
        bintray.start()
        def pb = pluginBuilder().addPlugin("", "test")
        publishPluginToBintray(pb, "test", "test")

        // resolution is currently not cached, 3 searches are going to happen
        bintray.api.expectPackageSearch("test", new BintrayApi.FoundPackage("foo", "test:test"))
        bintray.api.expectPackageSearch("test", new BintrayApi.FoundPackage("foo", "test:test"))
        bintray.api.expectPackageSearch("test", new BintrayApi.FoundPackage("foo", "test:test"))

        settingsFile << "include 'sub1', 'sub2'"

        ["sub1/build.gradle", "sub2/build.gradle", "build.gradle"].each {
            file(it) << """
                plugins {
                    apply plugin: "test", version: "$pluginVersion"
                }
                ext.pluginClass = org.gradle.test.TestPlugin
            """
        }

        file("build.gradle") << """
            evaluationDependsOnChildren()

            pluginClass.is project(":sub1").pluginClass
            pluginClass.is project(":sub2").pluginClass
            project(":sub1").pluginClass.is project(":sub2").pluginClass
        """

        then:
        succeeds "tasks"
    }

    @Ignore("not currently implemented, requirement in dispute")
    def "classes from plugin block are visible to classes from buildscript block"() {
        given:
        def pb1 = pluginBuilder()
        def pb2 = pluginBuilder()

        pb1.addPlugin("project.task('p1')", "p1", "PluginOne")
        pb2.addPlugin("apply plugin: PluginOne; project.task('p2').dependsOn(project.tasks.p1)", "p2", "PluginTwo")

        bintray.start()

        publishPluginToBintray(pb1, "p1", "p1")
        bintray.api.expectPackageSearch("p1", new BintrayApi.FoundPackage(pluginVersion, "p1:p1"))

        publishPluginToBintray(pb2, "p2", "p2")

        when:
        buildScript """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath "p2:p2:$pluginVersion"
                }
            }
            plugins {
                apply plugin: "p1"
            }

            apply plugin: "p2"
        """

        then:
        succeeds "p2"

        and:
        executedTasks == [":p1", ":p2"]
    }

}
