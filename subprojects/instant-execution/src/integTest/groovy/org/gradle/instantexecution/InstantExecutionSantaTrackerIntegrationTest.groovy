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

import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.Unroll


/**
 * Integration test Santa Tracker android app against AGP nightly.
 */
@LeaksFileHandles("TODO: AGP (intentionally) does not get a ‘build finished’ event and so does not close some files")
class InstantExecutionSantaTrackerIntegrationTest extends AbstractInstantExecutionAndroidIntegrationTest {

    def setup() {
        executer.beforeExecute {
            executer.noDeprecationChecks()
            executer.withRepositoryMirrors()
        }
    }

    @Unroll
    def "assembleDebug up-to-date on Santa Tracker #flavor (fromIde=#fromIde)"() {

        given:
        copyRemoteProject(remoteProject)
        withAgpNightly()

        when:
        instantRun("assembleDebug", "--no-build-cache", "-Pandroid.injected.invoked.from.ide=$fromIde")

        then:
        instantRun("assembleDebug", "--no-build-cache", "-Pandroid.injected.invoked.from.ide=$fromIde")

        where:
        flavor | remoteProject      | fromIde
        'Java' | "santaTrackerJava" | false
        'Java' | "santaTrackerJava" | true
        // TODO:instant-execution Kotlin 1.3.70
        // 'Kotlin' | "santaTrackerKotlin" | false
        // 'Kotlin' | "santaTrackerKotlin" | true
    }

    @Unroll
    def "clean assembleDebug on Santa Tracker #flavor (fromIde=#fromIde)"() {

        given:
        copyRemoteProject(remoteProject)
        withAgpNightly()

        when:
        instantRun("assembleDebug", "--no-build-cache", "-Pandroid.injected.invoked.from.ide=$fromIde")

        and:
        run 'clean'

        then:
        // Instant execution avoid registering the listener inside Android plugin
        instantRun("assembleDebug", "--no-build-cache", "-Pandroid.injected.invoked.from.ide=$fromIde")

        where:
        flavor | remoteProject      | fromIde
        'Java' | "santaTrackerJava" | false
        'Java' | "santaTrackerJava" | true
        // TODO:instant-execution Kotlin 1.3.70
        // 'Kotlin' | "santaTrackerKotlin" | false
        // 'Kotlin' | "santaTrackerKotlin" | true
    }
}
