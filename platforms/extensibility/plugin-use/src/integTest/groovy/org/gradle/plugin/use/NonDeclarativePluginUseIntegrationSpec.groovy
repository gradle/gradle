/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use

import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith

@LeaksFileHandles
class NonDeclarativePluginUseIntegrationSpec extends AbstractPluginSpec {

    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    def "non declarative plugin implementation can access core plugins and not core impl"() {
        given:
        publishPlugin """
            project.apply plugin: 'java'

            // Can see plugin classes
            getClass().classLoader.loadClass('org.gradle.api.plugins.JavaPlugin')

            // Can't see core impl classes
            def implClassName = 'com.google.common.collect.Multimap'
            project.getClass().getClassLoader().loadClass(implClassName)

            try {
                getClass().getClassLoader().loadClass(implClassName)
                assert false : "should have failed to load gradle implementation class: \$implClassName"
            } catch (ClassNotFoundException ignore) {

            }

            project.task('pluginTask')
        """

        when:
        buildFile USE

        then:
        succeeds("pluginTask")
    }

    def "plugin implementation and dependencies are visible to plugin and build script"() {
        given:
        def pluginBuilder2 = new PluginBuilder(file("plugin2"))
        pluginBuilder2.with {
            addPlugin("project.task('plugin2Task')", "test-plugin-2", "TestPlugin2")
            publishAs(GROUP, ARTIFACT + "2", VERSION, pluginRepo, executer).allowAll()
        }

        publishPlugin """
                // can load plugin dependent on
                project.apply plugin: 'test-plugin-2'

                // Can see dependency classes
                getClass().classLoader.loadClass('${pluginBuilder2.packageName}.TestPlugin2')

                project.task('pluginTask')
            """

        pluginRepo.module(GROUP, ARTIFACT, VERSION).dependsOn(GROUP, ARTIFACT + "2", VERSION).publishPom()

        when:
        buildFile """
            $USE

            def ops = []

            plugins.withId('$PLUGIN_ID') {
              ops << "withId 1"
            }

            plugins.withId("test-plugin-2") {
              ops << "withId 2"
            }

            def class1 = ${pluginBuilder.packageName}.TestPlugin
            def class2 = ${pluginBuilder2.packageName}.TestPlugin2

            plugins.withType(class1) {
              ops << "withType 1"
            }

            plugins.withType(class2) {
              ops << "withType 2"
            }

            apply plugin: '$PLUGIN_ID'
            apply plugin: 'test-plugin-2'

            println "ops = \$ops"
        """

        then:
        succeeds("pluginTask", "plugin2Task")

        and:
        output.contains 'ops = [withId 1, withId 2, withType 1, withType 2]'
    }

    def "classes from builscript and plugin block are visible in same build"() {
        given:
        def pluginBuilder2 = new PluginBuilder(file("plugin2"))
        pluginBuilder2.with {
            addPlugin("project.task('plugin2Task')", "test-plugin-2", "TestPlugin2")
            publishAs(GROUP, ARTIFACT + "2", VERSION, pluginRepo, executer).allowAll()
        }

        publishPlugin("").dependsOn(GROUP, ARTIFACT + "2", VERSION).publishPom()

        when:
        buildFile """
            buildscript {
              dependencies {
                classpath "$GROUP:${ARTIFACT + 2}:$VERSION"
              }
              repositories {
                maven { url = "$pluginRepo.uri" }
              }
            }
            $USE


            def class1 = ${pluginBuilder.packageName}.TestPlugin
            def class2 = ${pluginBuilder2.packageName}.TestPlugin2
        """

        then:
        succeeds("help")
    }

    def "dependencies of non declarative plugins influence buildscript dependency resolution"() {
        given:
        [1, 2].each { n ->
            def m = pluginRepo.module("test", "test", n as String)
            m.publish().allowAll()

            file("j$n").with {
                file("d/v.txt") << n
                m.artifactFile.delete()
                zipTo(m.artifactFile)
            }

        }

        when:
        def pluginModule = publishPlugin """
            project.tasks.register("pluginTask") {
                def resource = this.getClass().classLoader.getResource('d/v.txt')
                doLast {
                    println "pluginTask - " + resource.text
                }
            }
        """

        pluginModule.dependsOn("test", "test", "2").publishPom()

        and:
        buildFile """
            buildscript {
                repositories {
                    maven { url = "$pluginRepo.uri" }
                }
                dependencies {
                    classpath "test:test:1"
                }
            }

            $USE

            task scriptTask {
                def resource = this.getClass().classLoader.getResource('d/v.txt')
                doLast {
                    println "scriptTask - " + resource.text
                }
            }

            task buildscriptDependencies {
                def moduleVersion = buildscript.configurations.classpath.incoming.artifacts.artifacts
                    .collect { it.id }
                    .findAll { it.componentIdentifier instanceof ModuleComponentIdentifier }
                    .collect { it.componentIdentifier as ModuleComponentIdentifier }
                    .find { it.module == "test" }
                    .version

                doLast {
                    println "buildscriptDependencies - " + moduleVersion
                }
            }
        """

        then:
        succeeds "pluginTask", "scriptTask", "buildscriptDependencies"

        and:
        output.contains "pluginTask - 2"
        output.contains "scriptTask - 2"
        output.contains "buildscriptDependencies - 2"
    }

    def "failure due to no plugin with id in implementation"() {
        when:
        pluginBuilder.with {
            addPlugin(PLUGIN_ID, "other")
            publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll()
        }

        and:
        buildFile """
            $USE
        """

        then:
        fails "tasks"

        and:
        failure.assertThatDescription(startsWith("Plugin [id: 'org.myplugin', version: '1.0'] was not found in any of the following sources"))
        failure.assertThatDescription(containsString("""
            - Plugin Repositories (could not resolve plugin artifact 'org.myplugin:org.myplugin.gradle.plugin:1.0')
              Searched in the following repositories:
                Gradle Central Plugin Repository(${pluginRepo.uri})
        """.stripIndent().trim()))
        failure.assertHasLineNumber(2)
    }

    def "failure due to plugin class is unloadable"() {
        when:
        pluginBuilder.with {
            addUnloadablePlugin(PLUGIN_ID, "OtherPlugin")
            publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll()
        }

        and:
        buildFile """
            $USE
        """

        then:
        fails "tasks"

        and:
        failure.assertHasDescription("An exception occurred applying plugin request [id: 'org.myplugin', version: '1.0']")
        failure.assertHasCause("Could not create plugin of type 'OtherPlugin'.")
        failure.assertHasLineNumber(2)
    }

    def "failure due to plugin instantiation throwing"() {
        when:
        pluginBuilder.with {
            addNonConstructiblePlugin(PLUGIN_ID, "OtherPlugin")
            publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll()
        }

        and:
        buildFile """
            $USE
        """

        then:
        fails "tasks"

        and:
        failure.assertHasDescription("An exception occurred applying plugin request [id: 'org.myplugin', version: '1.0']")
        failure.assertHasCause("Could not create plugin of type 'OtherPlugin'.")
        failure.assertHasCause("broken plugin")
        failure.assertHasLineNumber(2)
    }

    def "failure due to plugin apply throwing"() {
        when:
        publishPlugin "throw new Exception('throwing plugin')"

        and:
        buildFile """
            $USE
        """

        then:
        fails "tasks"

        and:
        failure.assertHasDescription("An exception occurred applying plugin request [id: 'org.myplugin', version: '1.0']")
        failure.assertHasCause("throwing plugin")
        failure.assertHasLineNumber(2)
    }

}
