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
import spock.lang.Issue

class BuildSrcPluginTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2001") // when using the daemon
    def "can use plugin from buildSrc that changes"() {
        given:
        distribution.requireIsolatedDaemons() // make sure we get the same daemon both times

        buildFile << "apply plugin: 'test-plugin'"

        file("buildSrc/settings.gradle") << "include 'testplugin'"
        
        file("buildSrc/build.gradle") << """
            apply plugin: "groovy"
            dependencies {
                runtime project(":testplugin")
            }
        """
                
        file("buildSrc/testplugin/build.gradle") << """
            apply plugin: "groovy"

            dependencies {
                groovy localGroovy()
                compile gradleApi()
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


        file("buildSrc/testplugin/src/main/resources/META-INF/gradle-plugins/test-plugin.properties") << """\
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
}
