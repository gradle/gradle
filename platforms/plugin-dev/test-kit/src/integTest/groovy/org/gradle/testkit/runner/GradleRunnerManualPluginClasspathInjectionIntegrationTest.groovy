/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.testkit.runner.fixtures.PluginUnderTest

class GradleRunnerManualPluginClasspathInjectionIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def plugin = new PluginUnderTest(file("plugin"))

    /*
        Intentionally simplistic test so that it can run with any Gradle version.
     */

    def "can manually inject classes under test"() {
        given:
        plugin.build()
        buildFile << """
            buildscript {
                dependencies {
                    classpath files(${plugin.implClasspath.collect { "'${it.absolutePath.replace("\\", "\\\\")}'" }.join(", ")})
                }
            }

            apply plugin: '$plugin.id'
        """

        when:
        runner('helloWorld').build()

        then:
        file("out.txt").text == "Hello world!"
    }

}
