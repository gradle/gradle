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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.FailsWith
import spock.lang.Issue

// TODO: This needs a better home - Possibly in the test kit package in the future
class ApplyPluginIntegSpec extends AbstractIntegrationSpec {

    @Issue("GRADLE-2358")
    @FailsWith(UnexpectedBuildFailure) // Test is currently failing
    def "can reference plugin by id in unitest"() {

        given:
        file("src/main/groovy/org/acme/TestPlugin.groovy") << """
            package com.acme
            import org.gradle.api.*

            class TestPlugin implements Plugin<Project> {
                void apply(Project project) {
                    println "testplugin applied"
                }
            }
        """

        file("src/main/resources/META-INF/gradle-plugins/testplugin.properties") << "implementation-class=org.acme.TestPlugin"

        file("src/test/groovy/org/acme/TestPluginSpec.groovy") << """
            import spock.lang.Specification
            import ${ProjectBuilder.name}
            import ${Project.name}
            import com.acme.TestPlugin

            class TestPluginSpec extends Specification {
                def "can apply plugin by id"() {
                    when:
                    Project project = ProjectBuilder.builder().build()
                    project.apply(plugin: "testplugin")

                    then:
                    project.plugins.withType(TestPlugin).size() == 1
                }
            }
        """

        and:
        buildFile << '''
            apply plugin: 'groovy'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile gradleApi()
                compile localGroovy()
                compile "org.spockframework:spock-core:0.7-groovy-1.8", {
                    exclude module: "groovy-all"
                }
            }
        '''

        expect:
        succeeds("test")
    }
}