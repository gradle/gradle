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

package org.gradle.integtests.tooling.r63

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection

class DistributionChecksumCrossVersionSpec extends ToolingApiSpecification {
    // Newer clients no longer kill the JVM when a distribution is invalid.
    @TargetGradleVersion(">=3.0 <6.3")
    def "invalid Gradle distribution does not kill the TAPI client with older Gradle versions"() {
        given:
        toolingApi.requireDaemons()
        toolingApi.requireIsolatedUserHome()
        withConnection { connection ->
            connection.newBuild().forTasks("wrapper").run()
        }
        file('gradle/wrapper/gradle-wrapper.properties') << 'distributionSha256Sum=bad'

        when:
        def connector = this.connector()
        connector.useBuildDistribution()
        withConnection(connector) { ProjectConnection connection ->
            connection.newBuild().forTasks("help").run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.cause.message.contains("Verification of Gradle distribution failed!")
    }
}
