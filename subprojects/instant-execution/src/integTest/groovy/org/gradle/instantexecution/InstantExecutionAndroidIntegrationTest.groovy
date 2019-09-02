/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule


class InstantExecutionAndroidIntegrationTest extends AbstractInstantExecutionAndroidIntegrationTest {

    @Rule
    TestResources resources = new TestResources(temporaryFolder, "builds")

    def instantExecution

    def setup() {
        executer.noDeprecationChecks()
        executer.withRepositoryMirrors()

        def rootDir = file("android-3.6-mini")
        executer.beforeExecute {
            inDirectory(rootDir)
        }
        withAgpNightly(rootDir.file("build.gradle"))

        instantExecution = newInstantExecutionFixture()
    }

    def "android 3.6 minimal build assembleDebug --dry-run"() {

        when:
        instantRun("assembleDebug", "--dry-run")

        then:
        instantExecution.assertStateStored()

        when:
        instantRun("assembleDebug", "--dry-run")

        then:
        instantExecution.assertStateLoaded()
    }

    def "android 3.6 minimal build assembleDebug up-to-date"() {
        when:
        instantRun("assembleDebug")

        then:
        instantExecution.assertStateStored()

        when:
        instantRun("assembleDebug")

        then:
        instantExecution.assertStateLoaded()
    }

    def "android 3.6 minimal build clean assembleDebug"() {
        when:
        instantRun("assembleDebug")

        then:
        instantExecution.assertStateStored()

        when:
        run 'clean'
        instantRun("assembleDebug")

        then:
        instantExecution.assertStateLoaded()
    }
}
