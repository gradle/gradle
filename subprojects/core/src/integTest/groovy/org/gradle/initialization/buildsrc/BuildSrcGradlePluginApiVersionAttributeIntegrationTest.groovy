/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.initialization.buildsrc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion

class BuildSrcGradlePluginApiVersionAttributeIntegrationTest extends AbstractIntegrationSpec {

    def "buildSrc applies Gradle plugin API version attribute to source set classpath configurations"() {
        file("buildSrc/settings.gradle") << "include('sub')"

        file("buildSrc/sub/build.gradle") << """
            apply plugin: 'java-base'

            tasks.register('checkConfigurations') {
                doFirst {
                    configurations.each {
                        def attributeValue = it.attributes.getAttribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE)
                        if (attributeValue != null) {
                            throw new GradleException('Not expected the attribute to be set in subprojects, got: ' + attribute.value)
                        }
                    }
                }
            }
        """

        file('buildSrc/build.gradle') << """
            apply plugin: 'java-base'

            java.sourceSets.register('other')

            def task = tasks.register('findConfigurationsWithAttribute') {
                doFirst {
                    def getAttribute = { configuration -> configuration.attributes.getAttribute(
                        GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE
                    )}
                    def lines = configurations.matching { getAttribute(it) != null }.collect { it.name + '=' + getAttribute(it).name }
                    lines.each { println('>>> ' + it) }
                }
            }


            tasks.register("verify") {
                dependsOn(task)
                dependsOn(":sub:checkConfigurations")
            }
        """

        when:
        succeeds(":buildSrc:verify")

        then:
        def configurationsWithAttribute =
            output.findAll(">>> .*").collect {it.takeAfter(">>> ").split("=") }.collectEntries()

        configurationsWithAttribute ==
            ["buildScriptClasspath", "compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath", "otherCompileClasspath", "otherRuntimeClasspath"]
                .collectEntries { [it, GradleVersion.current().getVersion()] }
    }
}
