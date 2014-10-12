/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r22

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion(">=2.2")
class ClientShutdownCrossVersionSpec extends ToolingApiSpecification {
    def cleanup() {
        reset()
    }

    def "can shutdown tooling API session when no operations have been executed"() {
        given:
        DefaultGradleConnector.close()

        when:
        GradleConnector.newConnector()

        then:
        IllegalStateException e = thrown()
    }

    @TargetGradleVersion(">=2.2")
    def "cleans up daemons when tooling API session is shutdown"() {
        given:
        toolingApi.requireIsolatedDaemons()
        toolingApi.isEmbedded = false

        withConnection { connection ->
            connection.getModel(GradleBuild)
        }
        toolingApi.daemons.daemon.assertIdle()

        when:
        DefaultGradleConnector.close()

        then:
        toolingApi.daemons.daemon.stops()

        when:
        GradleConnector.newConnector()

        then:
        IllegalStateException e = thrown()
    }
}
