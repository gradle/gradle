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

import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import spock.lang.Specification

class UnsupportedOlderVersionConnectionTest extends Specification {
    final Distribution distribution = Mock(Distribution)
    final ConsumerOperationParameters parameters = Mock()
    final ConnectionVersion4 connection = Mock()
    final ConnectionMetaDataVersion1 metaDataVersion1 = Mock()
    final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter()

    def setup() {
        _ * connection.metaData >> metaDataVersion1
        _ * metaDataVersion1.version >> '1.0-milestoneX'
    }

    def "run fails"() {
        def connection = new UnsupportedOlderVersionConnection(connection, adapter)

        when:
        connection.run(GradleProject.class, parameters)

        then:
        UnsupportedVersionException e = thrown()
        e != null
    }

    def "partial BuildEnvironment"() {
        def connection = new UnsupportedOlderVersionConnection(connection, adapter)

        when:
        def buildEnv = connection.run(BuildEnvironment.class, parameters)

        then:
        buildEnv.gradle.gradleVersion == '1.0-milestoneX'
    }
}
