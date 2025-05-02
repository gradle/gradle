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
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.Issue

/**
 * Tests for classloading related bugs with a composite build.
 */
class CompositeBuildClassloadingIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    @Issue('GRADLE-3553')
    @LeaksFileHandles
    def "init-script with project dependent classpath and included build"() {
        given:
        file('gradle-user-home/init.gradle') << """
            initscript {
                ${mavenCentralRepository()}

                File searchDir = gradle.startParameter.projectDir ?: gradle.startParameter.currentDir
                def version = new File(searchDir, 'version.txt').text

                dependencies {
                    // Dynamically changing the classpath here surfaces problems with the ClassLoaderCache
                    classpath "org.apache.commons:commons-lang3:\${version}"
                }
            }

            rootProject {
                it.tasks.create('doStringOp') {
                    doLast {
                        println org.apache.commons.lang3.StringUtils.join('Hello', 'world')
                    }
                }
            }
        """.stripIndent()

        dependency 'org.test:buildB:1.0'

        BuildTestFile buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
            """.stripIndent()
        }
        includedBuilds << buildB

        buildA.file('version.txt') << '3.3'
        buildB.file('version.txt') << '3.4'

        executer.withGradleUserHomeDir(file('gradle-user-home'))

        expect:
        execute(buildA, "doStringOp")
        execute(buildA, "doStringOp")
    }
}
