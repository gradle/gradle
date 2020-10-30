/*
 * Copyright 2020 the original author or authors.
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

class CompositeBuildIncludeCycleIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    BuildTestFile buildB
    BuildTestFile buildC

    def setup() {
        buildB = singleProjectBuild("buildB")
        includedBuilds << buildB
        buildC = singleProjectBuild("buildC")
        includedBuilds << buildC
    }

    def "two included builds can include each other"() {
        when:
        buildB.settingsFile << "includeBuild '../buildC'"
        buildC.settingsFile << "includeBuild '../buildB'"

        then:
        execute(buildA, 'help')
    }

    def "included build can include root build"() {
        when:
        buildB.settingsFile << "includeBuild '../buildA'"
        buildC.settingsFile << "includeBuild '../buildA'"

        then:
        execute(buildA, 'help')
    }

    def "nested build can include root build"() {
        when:
        buildB.settingsFile << "includeBuild '../buildC'"
        buildC.settingsFile << "includeBuild '../buildA'"

        then:
        execute(buildA, 'help')
    }

    def "included build can see included root build"() {
        when:
        buildA.settingsFile << """
            rootProject.name = 'theNameOfBuildA'
        """
        buildB.settingsFile << "includeBuild '../buildA'"
        buildB.buildFile << """
            assert gradle.includedBuilds.collect { it.name } == ['theNameOfBuildA']
        """

        then:
        execute(buildA, 'help')
    }

    def "the root build cannot be renamed"() {
        when:
        buildA.settingsFile << """
            rootProject.name = 'theNameOfBuildA'
        """
        buildB.settingsFile << "includeBuild('../buildA') { name = 'some-other-name' }"
        buildB.buildFile << """
            assert gradle.includedBuilds.collect { it.name } == ['theNameOfBuildA']
        """

        then:
        execute(buildA, 'help')
    }

    def "included root build is only configured one time"() {
        given:
        buildA.buildFile << """
            println("Configuring \$identityPath")
        """
        buildB.settingsFile << "includeBuild '../buildA'"

        when:
        execute(buildA, 'help')

        then:
        output.count("Configuring :") == 1
        outputDoesNotContain("Configuring :buildA")
    }

    def "can address root build task from included build"() {
        given:
        buildA.buildFile << """
            tasks.register('task1') {
                dependsOn gradle.includedBuild('buildB').task(':task2')
            }
            tasks.register('task3') { }
        """
        buildA.settingsFile << """
            rootProject.name = 'theNameOfBuildA'
        """
        buildB.settingsFile << "includeBuild '../buildA'"
        buildB.buildFile << """
            tasks.register('task2') {
                dependsOn gradle.includedBuild('theNameOfBuildA').task(':task3')
            }
        """

        when:
        fails(buildA, 'task1')

        then:
        // If we get here, the tasks were all found and registered. Once the cycle restrictions back to the root build are removed, this test should pass.
        failure.assertHasDescription("Could not find build ':'")
        // result.assertTaskExecuted(':task3', ':buildB:task2', 'task1')
    }
}
