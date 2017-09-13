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

@ToolingApiVersion("current")
class ToolingApiUnsupportedProviderCrossVersionSpec extends ToolingApiVersionSpecification {
    @TargetGradleVersion("<1.2")
    def "fail in build execution for <1.2 providers"() {
        when:
        build()

        then:
        caughtGradleConnectionException = thrown()
        caughtGradleConnectionException.message.contains("Support for builds using Gradle versions older than 1.2 was removed in tooling API version 3.0. You are currently using Gradle version ${targetDist.version.version}. You should upgrade your Gradle build to use Gradle 1.2 or later.")
    }

    @TargetGradleVersion("<1.2")
    def "fail in model retrieval for <1.2 providers"() {
        when:
        getModel()

        then:
        caughtGradleConnectionException = thrown()
        caughtGradleConnectionException.message.contains("Support for builds using Gradle versions older than 1.2 was removed in tooling API version 3.0. You are currently using Gradle version ${targetDist.version.version}. You should upgrade your Gradle build to use Gradle 1.2 or later.")
    }

    @TargetGradleVersion("<1.2")
    def "fail in build action execution for <1.2 providers"() {
        when:
        buildAction()

        then:
        caughtGradleConnectionException = thrown()
        caughtGradleConnectionException.message.contains("The version of Gradle you are using (${targetDist.version.version}) does not support the BuildActionExecuter API. Support for this is available in Gradle 1.8 and all later versions.")
    }

    @TargetGradleVersion(">=1.2 <1.8")
    def "fail in build action execution for >=1.2 <1.8 providers"() {
        when:
        buildAction()

        then:
        caughtGradleConnectionException = thrown()
        caughtGradleConnectionException.message == "The version of Gradle you are using (${targetDist.version.version}) does not support the BuildActionExecuter API. Support for this is available in Gradle 1.8 and all later versions."
    }

    @TargetGradleVersion("<1.2")
    def "fail in test execution execution for <1.2 providers"() {
        when:
        testExecution()

        then:
        caughtGradleConnectionException = thrown()
        caughtGradleConnectionException.message.contains("The version of Gradle you are using (${targetDist.version.version}) does not support the TestLauncher API. Support for this is available in Gradle 2.6 and all later versions.")
    }

    @TargetGradleVersion(">=1.2 <2.6")
    def "fail in test execution execution for >=1.2 <2.6 providers"() {
        when:
        testExecution()

        then:
        caughtGradleConnectionException = thrown()
        caughtGradleConnectionException.message == "The version of Gradle you are using (${targetDist.version.version}) does not support the TestLauncher API. Support for this is available in Gradle 2.6 and all later versions."
    }
}
