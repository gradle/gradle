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


class CompositeBuildNestingIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    def "can nest included builds"() {
        given:
        dependency(buildA, "org.test:buildB:1.2")

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                apply plugin: 'java'
                dependencies { implementation 'org.test:buildC:1.2' }
            """
        }
        includeBuild(buildB)

        when:
        execute(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildC:jar")
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":jar")
    }

    def "a nested included build is substituted into all other builds"() {
        given:
        dependency(buildA, "org.test:buildB:1.2")
        dependency(buildA, "org.test:buildC:1.2")

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }

        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                apply plugin: 'java'
                dependencies { implementation 'org.test:buildD:1.2' }
                dependencies { implementation 'org.test:buildC:1.2' }
            """
        }
        includeBuild(buildB)

        def buildD = singleProjectBuild("buildD") {
            buildFile << """
                apply plugin: 'java'
                dependencies { implementation 'org.test:buildC:1.2' }
            """
        }
        includeBuild(buildD)

        when:
        execute(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildC:jar")
        result.assertTaskExecuted(":buildD:jar")
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":jar")
    }

    def "a build can be included by multiple other builds"() {
        given:
        dependency(buildA, "org.test:buildB:1.2")

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        includeBuild(buildC)
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                apply plugin: 'java'
                dependencies { implementation 'org.test:buildC:1.2' }
            """
        }
        includeBuild(buildB)

        when:
        execute(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildC:jar")
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":jar")
    }
}
