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

package org.gradle.testkit.runner

import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.tooling.UnsupportedVersionException

import static org.gradle.tooling.UnsupportedVersionException.*

@NonCrossVersion
class GradleRunnerUnsupportedGradleVersionFailureIntegrationTest extends BaseGradleRunnerIntegrationTest {
    def "fails informatively when trying to use unsupported gradle version"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion("1.1")
            .build()

        and:
        result.output

        then:
        def e = thrown UnsupportedVersionException
        e.message == "Support for Gradle older than ${MIN_PROVIDER_VERSION.version} has been removed. You should upgrade your Gradle to version ${MIN_PROVIDER_VERSION.version} or later."
    }
}
