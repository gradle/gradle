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

@Ignore // now outdated, tests are being incrementally extracted and adapted in new specs
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

    def setup() {
        executer.requireOwnGradleUserHomeDir() // to negate caching effects
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



    def "plugin classes are reused across projects and resolution is cached"() {
        when:
        bintray.start()
        def pb = pluginBuilder().addPlugin("", "test")
        publishPluginToBintray(pb, "test", "test")

        // Only receiving one search request verifies that the result is cached
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

    def "classes from plugin block are not visible to classes from buildscript block"() {
        given:
        def pb1 = pluginBuilder()
        def pb2 = pluginBuilder()

        pb1.addPlugin("getClass().classLoader.loadClass('org.gradle.test.PluginOne')", "p1", "PluginOne")
        pb2.addPlugin("getClass().classLoader.loadClass('org.gradle.test.PluginOne')", "p2", "PluginTwo")

        bintray.start()

        publishPluginToBintray(pb1, "p1", "p1")
        bintray.api.expectPackageSearch("p1", new BintrayApi.FoundPackage(pluginVersion, "p1:p1"))

        publishPluginToBintray(pb2, "p2", "p2")

        when:
        settingsFile << "rootProject.name = 'tp'"
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
                apply plugin: "p1", version: "1.0"
            }

            apply plugin: "p2"
        """

        then:
        fails "tasks"

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'tp'.")
        failure.assertHasCause("org.gradle.test.PluginOne") // message of ClassNotFoundException
        failure.assertHasLineNumber(14) // asserts that  'apply plugin: "p2"' is the line that fails
    }

    def "plugin classes are not available to child projects"() {
        given:
        bintray.start()
        def pb = pluginBuilder().addPlugin("", "plugin")
        publishPluginToBintray(pb, "plugin", "plugin")
        bintray.api.expectPackageSearch("plugin", new BintrayApi.FoundPackage(pluginVersion, "plugin:plugin"))

        when:
        settingsFile << "include 'child'"
        buildScript """
            ${testPluginPluginsBlock()}
            import org.gradle.test.TestPlugin
        """
        file("child/build.gradle") << """
            import org.gradle.test.TestPlugin
        """

        then:
        fails "tasks"

        and:
        failure.assertHasDescription("Could not compile build file '${file("child/build.gradle")}'.")
    }

    def "plugins cannot be applied by child projects"() {
        given:
        bintray.start()
        def pb = pluginBuilder().addPlugin("", "plugin")
        publishPluginToBintray(pb, "plugin", "plugin")
        bintray.api.expectPackageSearch("plugin", new BintrayApi.FoundPackage(pluginVersion, "plugin:plugin"))

        when:
        settingsFile << "include 'child'"
        buildScript """
            ${testPluginPluginsBlock()}
            import org.gradle.test.TestPlugin
        """
        file("child/build.gradle") << """
            apply plugin: 'plugin'
        """

        then:
        fails "tasks"

        and:
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
        failure.assertHasCause("Plugin with id 'plugin' not found.")
    }

    def "plugins added via plugins block cannot be applied with project.apply"() {
        given:
        bintray.start()
        def pb = pluginBuilder().addPlugin("project.task('foo')", "plugin")
        publishPluginToBintray(pb, "plugin", "plugin")
        bintray.api.expectPackageSearch("plugin", new BintrayApi.FoundPackage(pluginVersion, "plugin:plugin"))

        when:
        settingsFile << 'rootProject.name = "tp"'
        buildScript """
            ${testPluginPluginsBlock()}
            apply plugin: 'plugin'
        """

        then:
        fails "tasks"

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'tp'.")
        failure.assertHasCause("Plugin with id 'plugin' not found.")
    }

    def "plugins added in root buildscript can be applied in subprojects"() {
        given:
        executer.withEagerClassLoaderCreationCheckDisabled()
        pluginBuilder().addPlugin("project.task('foo')", "plugin").publishTo(mavenRepo.module("g", "a").artifactFile)

        settingsFile << "include 'sub'"

        buildScript """
            buildscript {
                repositories {
                    maven { url "$mavenRepo.uri" }
                }
                dependencies {
                    classpath "g:a:1.0"
                }
            }

            apply plugin: 'plugin'

            subprojects {
                apply plugin: 'plugin'
            }
        """

        when:
        succeeds "sub:foo"

        then:
        ":sub:foo" in executedTasks
    }
}
