/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.consumer.parameters.ConsumerConnectionParameters
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.BuildActionRunner
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.ConfigurableConnection
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import spock.lang.Specification

class BuildActionRunnerBackedConsumerConnectionTest extends Specification {
    final TestBuildActionRunner target = Mock()
    final ConsumerOperationParameters parameters = Mock()
    final BuildActionRunnerBackedConsumerConnection connection = new BuildActionRunnerBackedConsumerConnection(target, Mock(VersionDetails))

    def "configures connection"() {
        def parameters = new ConsumerConnectionParameters(false)

        when:
        connection.configure(parameters)

        then:
        1 * target.configure(parameters)
        0 * target._
    }

    def "builds model using run() method"() {
        BuildResult<String> result = Mock()

        given:
        result.model >> 'ok'

        when:
        def model = connection.run(String.class, parameters)

        then:
        model == 'ok'

        and:
        1 * target.run(String.class, parameters) >> result
        0 * target._
    }

    interface TestBuildActionRunner extends ConnectionVersion4, BuildActionRunner, ConfigurableConnection {
    }
}
