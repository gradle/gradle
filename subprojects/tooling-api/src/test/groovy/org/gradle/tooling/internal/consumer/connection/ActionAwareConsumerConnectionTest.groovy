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
import org.gradle.tooling.BuildController
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.*
import spock.lang.Specification

class ActionAwareConsumerConnectionTest extends Specification {
    final target = Mock(TestModelBuilder) {
        getMetaData() >> Mock(ConnectionMetaDataVersion1)
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
        result == '[result]'

        and:
        1 * target.run(_, _, parameters) >> { InternalBuildAction protocolAction, def serializeDetails, def params ->
            def actionResult = protocolAction.execute(buildController)
            return Stub(BuildResult) {
                getModel() >> actionResult
            }
        }
        1 * action.execute(_) >> { BuildController controller ->
            return controller.getModel(String)
        }
        1 * buildController.getModel(_) >> {
            return Stub(BuildResult) {
                getModel() >> 'result'
            }
        }
        1 * adapter.adapt(String, 'result') >> '[result]'
    }

    interface TestModelBuilder extends ModelBuilder, ConnectionVersion4, ConfigurableConnection, InternalBuildActionExecutor {
    }
}
