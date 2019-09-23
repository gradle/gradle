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

import spock.lang.Unroll


/**
 * Integration test Santa Tracker android app against AGP nightly.
 */
class InstantExecutionSantaTrackerIntegrationTest extends AbstractInstantExecutionAndroidIntegrationTest {

    def setup() {
        executer.beforeExecute {
            executer.noDeprecationChecks()
            executer.withRepositoryMirrors()
        }
    }

    @Unroll
    def "assembleDebug --dry-run on Santa Tracker #flavor"() {

        given:
        copyRemoteProject(remoteProject)
        withAgpNightly()

        when:
        instantRun ':santa-tracker:assembleDebug', '--dry-run', '--no-build-cache'

        then:
        instantRun ':santa-tracker:assembleDebug', '--dry-run', '--no-build-cache'

        where:
        flavor | remoteProject
        'Java' | "santaTrackerJava"
        // 'Kotlin' | "santaTrackerKotlin" // TODO:instant-execution Instant execution state could not be cached.
    }

    def "assembleDebug up-to-date on Santa Tracker Java"() {
        given:
        copyRemoteProject("santaTrackerJava")
        withAgpNightly()

        when:
        instantRun("assembleDebug", "--no-build-cache")

        then:
        instantRun("assembleDebug", "--no-build-cache")
    }

    def "supported tasks clean assembleDebug on Santa Tracker Java"() {

        given:
        copyRemoteProject("santaTrackerJava")
        withAgpNightly()

        when:
        instantRun("assembleDebug", "--no-build-cache")

        and:
        run 'clean'

        then:
        instantRun("assembleDebug", "--no-build-cache")
    }
}
