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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile

/**
 * Tests for init-script usage with a composite build.
 */
class CompositeBuildInitScriptIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        dependency 'org.test:buildB:1.0'

        buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        file('gradle-user-home/init.gradle') << """
allprojects { project ->
    println "Project " + project.name
    project.ext.initProperty = "foo"
}
"""
        includedBuilds << buildB
    }

    def "passes init-script arg to included build"() {
        given:
        [buildA, buildB].each {
            it.buildFile << """
    assert gradle.startParameter.initScripts.size() == 1
    assert project.initProperty == "foo"
"""
        }

        when:
        execute(buildA, ":jar", ["-I../gradle-user-home/init.gradle"])

        then:
        executed ":buildB:jar"
    }

    def "uses conventional init-script in included build"() {

        given:
        [buildA, buildB].each {
            it.buildFile << """
    assert gradle.startParameter.initScripts.size() == 0
    assert project.initProperty == "foo"
"""
        }

        when:
        executer.withGradleUserHomeDir(file('gradle-user-home'))
        execute(buildA, ":jar")

        then:
        executed ":buildB:jar"
    }
}
