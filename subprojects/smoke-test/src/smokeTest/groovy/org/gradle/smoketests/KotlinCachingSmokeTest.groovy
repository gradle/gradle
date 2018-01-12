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

package org.gradle.smoketests

import org.eclipse.jgit.api.Git
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext

class KotlinCachingSmokeTest extends AbstractSmokeTest {

    def "can cache Spek framework build"() {
        def testRepoUri = "https://github.com/gradle/kotlin-relocation-test"
        def testRepoBranch = "master"

        def projectDir = testProjectDir.root

        println "> Cloning $testRepoUri branch $testRepoBranch..."

        def clone = Git.cloneRepository()
        clone.URI = testRepoUri
        clone.branch = testRepoBranch
        clone.directory = projectDir
        clone.cloneSubmodules = true
        def git = clone.call()

        def commitId = git.repository.findRef("HEAD").objectId.name()
        println "> Building commit $commitId..."

        expect:
        runner(
                "check",
                "-Dorg.gradle.kotlin.test.gradle-installation=" + IntegrationTestBuildContext.INSTANCE.gradleHomeDir.absolutePath,
                "-Dorg.gradle.kotlin.test.show-output=true",
                "-Dorg.gradle.kotlin.test.scan-url=https://e.grdev.net/"
            )
            .withProjectDir(projectDir)
            .forwardOutput()
            .build()
    }
}
