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

import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.adapter.ViewBuilder
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.ModelBuilder
import org.gradle.tooling.internal.protocol.ModelIdentifier
import spock.lang.Specification

class ModelBuilderBackedModelProducerTest extends Specification {

    ProtocolToModelAdapter adapter = Mock(ProtocolToModelAdapter);
    VersionDetails versionDetails = Mock(VersionDetails);
    ModelMapping mapping = Mock(ModelMapping);
    ModelBuilder builder = Mock(ModelBuilder);

    ModelBuilderBackedModelProducer modelProducer

    def setup() {
        _ * versionDetails.getVersion() >> "1.0"
        modelProducer = new ModelBuilderBackedModelProducer(adapter, versionDetails, mapping, builder);
    }

    def "builder not triggered for unsupported Models"() {
        setup:
        1 * versionDetails.maySupportModel(SomeModel.class) >> false
        when:
        modelProducer.produceModel(SomeModel.class, Mock(ConsumerOperationParameters))
        then:
        0 * builder.getModel(_, _)
        def e = thrown(UnknownModelException)
        e.message == "The version of Gradle you are using (1.0) does not support building a model of type 'SomeModel'. Support for building custom tooling models was added in Gradle 1.6 and is available in all later versions."
    }

    def "builder triggered for supported Models"() {
        setup:
        SomeModel returnValue = new SomeModel()
        1 * versionDetails.maySupportModel(SomeModel.class) >> true
        ModelIdentifier someModelIdentifier = Mock(ModelIdentifier)
        1 * mapping.getModelIdentifierFromModelType(SomeModel.class) >> someModelIdentifier
        BuildResult buildResult = Mock(BuildResult)
        ConsumerOperationParameters operationParameters = Stub(ConsumerOperationParameters)
        ViewBuilder<SomeModel> viewBuilder = Mock(ViewBuilder)

        when:
        SomeModel model = modelProducer.produceModel(SomeModel.class, operationParameters)
        then:
        1 * builder.getModel(someModelIdentifier, operationParameters) >> buildResult
        1 * buildResult.model >> returnValue
        1 * adapter.builder(SomeModel.class) >> viewBuilder
        1 * viewBuilder.build(returnValue) >> returnValue
        model != null
    }

    static class SomeModel {

    }
}
