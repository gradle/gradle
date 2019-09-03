/*
 * Copyright 2017 the original author or authors.
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

class AndroidCachingSmokeTest extends AbstractSmokeTest {

    def "can cache Santa Tracker Android application"() {
        def testRepoUri = "https://github.com/gradle/android-relocation-test"
        def testRepoTarget = "63c4e4c25db4a21e6694fe3e31f20f77da403b6a"

        def projectDir = testProjectDir.root

        println "> Cloning $testRepoUri"

        def git = Git.cloneRepository()
            .setURI(testRepoUri)
            .setDirectory(projectDir)
            .setCloneSubmodules(true)
            .call()

        println "> Checking out $testRepoTarget"
        git.checkout()
            .setName(testRepoTarget)
            .call()

        def commitId = git.repository.findRef("HEAD").objectId.name()
        println "> Building commit $commitId"

        expect:
        runner(
                "check",
                "-Dorg.gradle.android.test.gradle-installation=" + IntegrationTestBuildContext.INSTANCE.gradleHomeDir.absolutePath,
                "-Dorg.gradle.android.test.show-output=true",
                "-Dorg.gradle.android.test.scan-url=https://e.grdev.net/"
            )
            .withProjectDir(projectDir)
            .forwardOutput()
            .build()
    }
}
