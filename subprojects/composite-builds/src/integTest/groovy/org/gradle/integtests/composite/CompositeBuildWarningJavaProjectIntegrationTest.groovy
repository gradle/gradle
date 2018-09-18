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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompositeBuildWarningJavaProjectIntegrationTest extends AbstractIntegrationSpec {

    def "No warning is shown for a non-composite build"() {
        given:
        singleProjectBuild("project") {
            buildFile << "apply plugin: 'java'"
        }

        when:
        succeeds 'buildDependents'

        then:
        output.count(warningMessage(':buildDependents')) == 0
    }

    def "No warning is shown for non-composite multi-project builds"() {
        given:
        multiProjectBuild("multi-project", ['sub1', 'sub2']) {
            buildFile << """
                subprojects {
                    apply plugin: 'java'
                }
            """
        }

        when:
        succeeds 'buildDependents'

        then:
        output.count(warningMessage(':buildDependents')) == 0
    }

    def "Shows warning when buildDependents task executed on single-project build"() {
        given:
        singleProjectBuild("project") {
            buildFile << "apply plugin: 'java'"
            settingsFile << "includeBuild 'included'"
            file('included', 'settings.gradle').touch()
        }

        when:
        succeeds 'buildDependents'

        then:
        output.count(warningMessage(':buildDependents')) == 1
    }

    def "Shows warning for all buildDependents tasks executed on multi-project build"() {
        given:
        multiProjectBuild("multi-project", ['sub1', 'sub2']) {
            buildFile << """
                subprojects {
                    apply plugin: 'java'
                }
            """
            settingsFile << "includeBuild 'included'"
            file('included', 'settings.gradle').touch()
        }

        when:
        succeeds 'buildDependents'

        then:
        output.count(warningMessage(':sub1:buildDependents')) == 1
        output.count(warningMessage(':sub2:buildDependents')) == 1
    }

    private String warningMessage(String taskPath) {
        "[composite-build] Warning: `$taskPath` task does not build included builds."
    }
}
