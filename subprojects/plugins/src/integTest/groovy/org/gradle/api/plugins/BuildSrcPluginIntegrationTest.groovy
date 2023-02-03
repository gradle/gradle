/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Issue

class BuildSrcPluginIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2001")
    def "can use plugin from buildSrc that changes"() {
        given:
        executer.requireIsolatedDaemons() // make sure we get the same daemon both times

        buildFile << "apply plugin: 'test-plugin'"

        file("buildSrc/settings.gradle") << "include 'testplugin'"

        file("buildSrc/build.gradle") << """
            apply plugin: "groovy"
            dependencies {
                runtimeOnly project(":testplugin")
            }
        """

        file("buildSrc/testplugin/build.gradle") << """
            apply plugin: "groovy"

            dependencies {
                implementation localGroovy()
                implementation gradleApi()
            }
        """

        def pluginSource = file("buildSrc/testplugin/src/main/groovy/testplugin/TestPlugin.groovy") << """
            package testplugin
            import org.gradle.api.Plugin

            class TestPlugin implements Plugin {
                void apply(project) {
                    project.task("echo").doFirst {
                        println "hello"
                    }
                }
            }
        """

        file("buildSrc/testplugin/src/main/resources/META-INF/gradle-plugins/test-plugin.properties") << """
            implementation-class=testplugin.TestPlugin
        """

        when:
        succeeds "echo"

        then:
        output.contains "hello"

        when:
        pluginSource.write """
            package testplugin
            import org.gradle.api.Plugin

            class TestPlugin implements Plugin {
                void apply(project) {
                    project.task("echo").doFirst {
                        println "hello again"
                    }
                }
            }
        """

        and:
        succeeds "echo"

        then:
        output.contains "hello again"
    }

    @IgnoreIf({ GradleContextualExecuter.embedded }) // In embedded testing mode, the visibility constraints are not enforced
    def "build src plugin cannot access Gradle implementation dependencies"() {
        when:
        file("buildSrc/src/main/groovy/pkg/BuildSrcPlugin.groovy") << """
            package pkg
            import ${com.google.common.collect.ImmutableList.name}
            class BuildSrcPlugin {

            }
        """

        then:
        fails "t"
        failure.assertHasDescription("Execution failed for task ':buildSrc:compileGroovy'.")
    }

    def "use of buildSrc does not expose Gradle runtime dependencies to build script"() {
        when:
        file("buildSrc/src/main/groovy/pkg/BuildSrcPlugin.groovy") << """
            package pkg
            class BuildSrcPlugin {

            }
        """

        buildFile << """
            import ${com.google.common.collect.ImmutableList.name}
        """

        then:
        fails "t"
        failure.assertHasDescription("Could not compile build file '$buildFile.canonicalPath'.")
    }

    def "build uses jar from buildSrc"() {
        writeBuildSrcPlugin("buildSrc", "MyPlugin")
        buildFile << """
            apply plugin: MyPlugin
            // nuke buildSrc classes so we can't use them
            project.delete(file("buildSrc/build/classes"))
        """
        when:
        succeeds("myTaskMyPlugin")
        then:
        outputContains("From MyPlugin")
    }

    def "build uses jars from multi-project buildSrc"() {
        writeBuildSrcPlugin("buildSrc", "MyPlugin")
        writeBuildSrcPlugin("buildSrc/subproject", "MyPluginSub")
        file("buildSrc/build.gradle") << """
            allprojects {
                apply plugin: 'groovy'
                dependencies {
                    implementation gradleApi()
                    implementation localGroovy()
                }
            }
            dependencies {
                runtimeOnly project(":subproject")
            }
        """
        file("buildSrc/settings.gradle") << """
            include 'subproject'
        """
        buildFile << """
            apply plugin: MyPlugin
            apply plugin: MyPluginSub
            // nuke buildSrc classes so we can't use them
            project.delete(file("buildSrc/build/classes"))
            project.delete(file("buildSrc/subproject/build/classes"))
        """
        when:
        succeeds("myTaskMyPlugin", "myTaskMyPluginSub")
        then:
        outputContains("From MyPlugin")
        outputContains("From MyPluginSub")
    }

    def "Default buildSrc root project dependencies are on the api"() {
        when:
        file("buildSrc/settings.gradle") << "include ':subInBuildSrc'"
        file("buildSrc/subInBuildSrc/build.gradle") << """
            plugins { id 'java-library' }
            dependencies { implementation(project(':')) }
        """
        file("buildSrc/subInBuildSrc/src/main/java/MySubProjectClass.java") << """
            import org.gradle.api.Project;
            import groovy.lang.Closure;
            public class MySubProjectClass {
                private Project p;
                private Closure c;
            }
        """
        then:
        succeeds ":buildSrc:subInBuildSrc:assemble"
    }

    private void writeBuildSrcPlugin(String location, String className) {
        file("${location}/src/main/groovy/${className}.groovy") << """
            import org.gradle.api.*

            class ${className} implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.create("myTask${className}") {
                        doLast {
                            def closure = {
                                println "From ${className}"
                            }
                            closure()
                        }
                    }
                }
            }
        """
    }
}
