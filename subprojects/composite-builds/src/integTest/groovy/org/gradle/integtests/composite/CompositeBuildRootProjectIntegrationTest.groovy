/*
 * Copyright 2018 the original author or authors.
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

class CompositeBuildRootProjectIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "root of a composite build cannot refer to own subprojects by GA coordinates by default"() {
        given:
        def buildB = multiProjectBuild("buildB", ['c1', 'c2', 'c3']) {
            buildFile << """
            allprojects {
                apply plugin: 'java-library'
            }
            """
        }
        dependency(buildB, "org.test:c1")
        dependency(buildB, "org.test:c2:1.0")
        dependency(new BuildTestFile(buildB.file('c3'), 'c3'), "org.test:buildB") // dependency to root

        buildB.settingsFile << """
            includeBuild('${buildA.toURI()}') // include another build to become a composite
        """

        when:
        fails(buildB, "c3:jar")

        then:
        failure.assertHasCause("Cannot resolve external dependency org.test:buildB because no repositories are defined.")
    }

    def "root of a composite build can refer to own subprojects by GA coordinates when including itself"() {
        given:
        def buildB = multiProjectBuild("buildB", ['c1', 'c2', 'c3']) {
            buildFile << """
            allprojects {
                apply plugin: 'java-library'
            }
            """
        }
        dependency(buildB, "org.test:c1")
        dependency(buildB, "org.test:c2:1.0")
        dependency(new BuildTestFile(buildB.file('c3'), 'c3'), "org.test:buildB") // dependency to root

        buildB.settingsFile << """
            includeBuild('${buildA.toURI()}') // include another build to become a composite
            includeBuild('.')
        """

        when:
        execute(buildB, "c3:jar")

        then:
        result.assertTaskExecuted(":c1:compileJava")
        result.assertTaskExecuted(":c2:compileJava")
        result.assertTaskExecuted(":c3:compileJava")
        result.assertTaskExecuted(":compileJava")
    }

    def "included build can depend on including build"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java-library'
            """
        }
        buildA.buildFile << """
            // add lifecycle task here since we can not call the task of buildB directly yet - https://github.com/gradle/gradle/issues/2533
           tasks.register("buildBJar") {
                dependsOn(gradle.includedBuild("buildB").task(":jar"))
            }
        """
        buildA.settingsFile << """
            includeBuild('.')
        """

        includeBuild(buildB)
        dependency(buildB, "org.test:buildA")

        when:
        execute(buildA, "buildBJar")

        then:
        result.assertTaskExecuted(":compileJava")
        result.assertTaskExecuted(":jar")
        result.assertTaskExecuted(":buildB:compileJava")
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":buildBJar")
    }
}
