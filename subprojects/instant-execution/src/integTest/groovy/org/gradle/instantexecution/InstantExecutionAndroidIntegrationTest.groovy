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
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Rule
import spock.lang.Unroll

@LeaksFileHandles("TODO: AGP (intentionally) does not get a ‘build finished’ event and so does not close some files")
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

    @Unroll
    def "android 3.6 minimal build assembleDebug up-to-date (fromIde=#fromIde)"() {

        when:
        instantRun("assembleDebug", "-Pandroid.injected.invoked.from.ide=$fromIde")

        then:
        instantExecution.assertStateStored()

        when:
        instantRun("assembleDebug", "-Pandroid.injected.invoked.from.ide=$fromIde")

        then:
        instantExecution.assertStateLoaded()

        where:
        fromIde << [false, true]
    }

    @Unroll
    def "android 3.6 minimal build clean assembleDebug (fromIde=#fromIde)"() {

        when:
        instantRun("assembleDebug", "-Pandroid.injected.invoked.from.ide=$fromIde")

        then:
        instantExecution.assertStateStored()

        when:
        run 'clean'
        instantRun("assembleDebug", "-Pandroid.injected.invoked.from.ide=$fromIde")

        then:
        instantExecution.assertStateLoaded()

        where:
        fromIde << [false, true]
    }
}
