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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.api.Action
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.adapter.SourceObjectMapping
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.*
import spock.lang.Specification

class CancellableConsumerConnectionTest extends Specification {
    final target = Mock(TestModelBuilder) {
        getMetaData() >> Stub(ConnectionMetaDataVersion1) {
            getVersion() >> "2.1"
        }
    }
    final adapter = Mock(ProtocolToModelAdapter)
    final modelMapping = Mock(ModelMapping)
    final connection = new CancellableConsumerConnection(target, modelMapping, adapter)

    def "delegates to connection to run build action"() {
        def action = Mock(BuildAction)
        def parameters = Stub(ConsumerOperationParameters)
        def buildController = Mock(InternalBuildController)

        when:
        def result = connection.run(action, parameters)

        then:
        result == 'result'

        and:
        1 * target.run(_, _, parameters) >> { InternalBuildAction protocolAction, InternalCancellationToken cancel, def params ->
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
        1 * target.run(_, _, parameters) >> { throw new InternalBuildActionFailureException(failure) }
    }

    def "runs build using connection's getModel() method"() {
        def parameters = Stub(ConsumerOperationParameters)
        ModelIdentifier modelIdentifier = Mock()

        when:
        def result = connection.run(Void.class, parameters)

        then:
        result == 'result'

        and:
        1 * modelMapping.getModelIdentifierFromModelType(Void) >> modelIdentifier
        1 * target.getModel(modelIdentifier, _, parameters) >> { ModelIdentifier id, InternalCancellationToken cancel, def params ->
            return Stub(BuildResult) {
                getModel() >> 'result'
            }
        }
        1 * adapter.adapt(Void, 'result', _) >> { Class type, Object source, Action<? super SourceObjectMapping> mapper ->
            return source
        }
    }

    interface TestModelBuilder extends ConnectionVersion4, ConfigurableConnection, InternalCancellableConnection {
    }
}
