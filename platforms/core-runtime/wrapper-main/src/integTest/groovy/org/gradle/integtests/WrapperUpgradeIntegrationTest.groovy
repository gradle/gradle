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

package org.gradle.integtests

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperUpgradeIntegrationTest extends AbstractWrapperIntegrationSpec {

    def "can run the wrapper task when the build was started with the wrapper"() {
        given:
        prepareWrapper()

        expect:
        wrapperExecuter.withTasks('wrapper').run()
    }

    def "prints helpful error message on invalid version argument format: #badVersion"() {
        given:
        prepareWrapper()

        expect:
        def failure = wrapperExecuter.withTasks("wrapper", "--gradle-version", badVersion).runWithFailure()

        and:
        failure.assertHasCause("Invalid version specified for argument '--gradle-version': '$badVersion'. Valid examples: 1.0, 1.0-rc-1, latest, nightly.")
        failure.assertHasResolution("Specify a valid Gradle release listed on https://gradle.org/releases/.")
        failure.assertHasResolution("Use one of the following dynamic version specifications: 'latest', 'release-candidate', 'release-nightly', 'nightly'.")

        where:
        badVersion << ["bad-version", "next", "new", "5.x", "x.3", "x+1", "8.5.x", "8.5.latest", "later", "prerelease", "nightly-release", "latest-release", "rc", "current"]
    }
}
