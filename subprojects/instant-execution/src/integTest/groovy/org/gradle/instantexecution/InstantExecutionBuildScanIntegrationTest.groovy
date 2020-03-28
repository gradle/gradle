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

package org.gradle.instantexecution

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll


class InstantExecutionBuildScanIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "--scan -Dscan.dump works"() {
        given:
        settingsKotlinFile << '''
            plugins {
                `gradle-enterprise`
            }

            gradleEnterprise.buildScan {
                termsOfServiceUrl = "https://gradle.com/terms-of-service"
                termsOfServiceAgree = "yes"
            }
        '''

        when:
        instantRun "help", "--scan", "-Dscan.dump"

        then:
        postBuildOutputContains("Build scan written to")

        when:
        instantRun "help", "--scan", "-Dscan.dump"

        then:
        postBuildOutputContains("Build scan written to")
    }

    @Requires(TestPrecondition.ONLINE)
    def "can publish build scans using --scan"() {

        given:
        def instant = newInstantExecutionFixture()
        settingsKotlinFile << '''
            plugins {
                `gradle-enterprise`
            }

            gradleEnterprise.buildScan {
                termsOfServiceUrl = "https://gradle.com/terms-of-service"
                termsOfServiceAgree = "yes"
            }
        '''

        when:
        instantRun "help", "--scan"

        then:
        instant.assertStateStored()
        postBuildOutputContains("Publishing build scan...")

        when:
        instantRun "help", "--scan"

        then:
        instant.assertStateLoaded()
        postBuildOutputContains("Publishing build scan...")
    }

    @Unroll
    def "can publish build scans to custom server using #customServerProperty"() {

        given:
        def instant = newInstantExecutionFixture()
        settingsKotlinFile << """
            plugins {
                `gradle-enterprise`
            }

            gradleEnterprise.buildScan {
                termsOfServiceUrl = "https://gradle.com/terms-of-service"
                termsOfServiceAgree = "yes"
            }
            $customServerProperty = "https://does.not.exists"
        """

        when:
        instantRun "help", "--scan"

        then:
        instant.assertStateStored()
        postBuildOutputContains("The hostname 'does.not.exists' could not be resolved.")

        when:
        instantRun "help", "--scan"

        then:
        instant.assertStateLoaded()
        postBuildOutputContains("The hostname 'does.not.exists' could not be resolved.")

        where:
        customServerProperty << ["gradleEnterprise.server", "gradleEnterprise.buildScan.server"]
    }
}
