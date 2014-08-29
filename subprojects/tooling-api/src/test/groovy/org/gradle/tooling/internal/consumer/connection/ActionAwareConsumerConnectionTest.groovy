/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.*
import spock.lang.Specification

class ActionAwareConsumerConnectionTest extends Specification {
    final target = Mock(TestModelBuilder) {
        getMetaData() >> Stub(ConnectionMetaDataVersion1) {
            getVersion() >> "1.8"
        }
    }
    final adapter = Mock(ProtocolToModelAdapter)
    final modelMapping = Stub(ModelMapping)
    final connection = new ActionAwareConsumerConnection(target, modelMapping, adapter)

    def "delegates to connection to run build action"() {
        def action = Mock(BuildAction)
        def parameters = Stub(ConsumerOperationParameters)
        def buildController = Mock(InternalBuildController)

        when:
        def result = connection.run(action, parameters)

        then:
        result == 'result'

        and:
        1 * target.run(_, parameters) >> { InternalBuildAction protocolAction, def params ->
            def actionResult = protocolAction.execute(buildController)
            return Stub(BuildResult) {
                getModel() >> actionResult
            }
        }
        1 * action.execute({ it instanceof BuildControllerAdapter }) >> 'result'
    }

    def "adapts build action failure"() {
        def action = Mock(BuildAction)
        def parameters = Stub(ConsumerOperationParameters)
        def failure = new RuntimeException()

        when:
        connection.run(action, parameters)

        then:
        BuildActionFailureException e = thrown()
        e.message == /The supplied build action failed with an exception./
        e.cause == failure

        and:
        1 * target.run(_, parameters) >> { throw new InternalBuildActionFailureException(failure) }
    }

    interface TestModelBuilder extends ModelBuilder, ConnectionVersion4, ConfigurableConnection, InternalBuildActionExecutor {
    }
}
