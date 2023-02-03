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
import org.gradle.tooling.GradleConnectionException

@ToolingApiVersion("current")
class ToolingApiUnsupportedProviderCrossVersionSpec extends ToolingApiVersionSpecification {
    @TargetGradleVersion("<2.6")
    def "fail in build execution for <2.6 providers"() {
        when:
        build()

        then:
        GradleConnectionException connectionException = thrown()
        connectionException.message.contains(unsupportedGradleVersion())
    }

    @TargetGradleVersion("<2.6")
    def "fail in model retrieval for <2.6 providers"() {
        when:
        getModel()

        then:
        GradleConnectionException connectionException = thrown()
        connectionException.message.contains(unsupportedGradleVersion())
    }

    @TargetGradleVersion("<2.6")
    def "fail in build action execution for <2.6 providers"() {
        when:
        buildAction()

        then:
        GradleConnectionException connectionException = thrown()
        connectionException.message.contains(unsupportedGradleVersion())
    }

    @TargetGradleVersion("<2.6")
    def "fail in test execution for <2.6 providers"() {
        when:
        testExecution()

        then:
        GradleConnectionException connectionException = thrown()
        connectionException.message.contains(unsupportedGradleVersion())
    }

    @TargetGradleVersion("<2.6")
    def "fail notifying daemons about changed paths for <2.6 providers"() {
        when:
        notifyDaemonsAboutChangedPaths()

        then:
        GradleConnectionException connectionException = thrown()
        connectionException.message.contains(unsupportedGradleVersion())
    }

    private String unsupportedGradleVersion() {
        "Support for builds using Gradle versions older than 2.6 was removed in tooling API version 5.0. You are currently using Gradle version ${targetDist.version.version}. You should upgrade your Gradle build to use Gradle 2.6 or later."
    }
}
