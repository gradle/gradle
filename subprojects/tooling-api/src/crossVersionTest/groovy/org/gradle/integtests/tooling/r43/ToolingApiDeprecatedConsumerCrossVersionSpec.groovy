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

package org.gradle.integtests.tooling.r43

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.util.GradleVersion

@TargetGradleVersion("current")
class ToolingApiDeprecatedConsumerCrossVersionSpec extends ToolingApiVersionSpecification {
    @ToolingApiVersion(">=2.0 <3.0")
    def "provider warns >=2.0 <3.0 tooling API client for build request"() {
        when:
        build()

        then:
        output.count(consumerDeprecationMessage(GradleVersion.current().version)) == 1
    }

    @ToolingApiVersion(">=2.0 <3.0")
    def "provider warns >=2.0 <3.0 tooling API client for model retrieval"() {
        when:
        getModel()

        then:
        output.count(consumerDeprecationMessage(GradleVersion.current().version)) == 1
    }

    @ToolingApiVersion(">=2.0 <3.0")
    def "provider warns >=2.0 <3.0 tooling API client for build action"() {
        when:
        buildAction()

        then:
        output.count(consumerDeprecationMessage(GradleVersion.current().version)) == 1
    }

    @ToolingApiVersion(">=2.6 <3.0")
    def "provider warns >=2.6 <3.0 tooling API client for test execution"() {
        when:
        testExecution()

        then:
        output.count(consumerDeprecationMessage(GradleVersion.current().version)) == 1
    }
}
