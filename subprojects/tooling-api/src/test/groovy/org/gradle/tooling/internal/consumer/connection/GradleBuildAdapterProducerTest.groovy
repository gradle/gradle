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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.ModelBuilder
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import spock.lang.Specification

class GradleBuildAdapterProducerTest extends Specification {
    ProtocolToModelAdapter adapter = Mock(ProtocolToModelAdapter);
    ModelMapping mapping = Mock(ModelMapping);
    ModelBuilder builder = Mock(ModelBuilder);
    ModelProducer delegate = Mock(ModelProducer)

    GradleBuildAdapterProducer modelProducer = new GradleBuildAdapterProducer(adapter, delegate);

    def "requests GradleProject on delegate when unsupported GradleBuild requested"() {
        setup:
        GradleProject gradleProject = gradleProject()
        ConsumerOperationParameters operationParameters = Mock(ConsumerOperationParameters)
        adapter.adapt(GradleProject, gradleProject) >> gradleProject
        adapter.adapt(GradleBuild, _) >> Mock(GradleBuild)
        when:
        def model = modelProducer.produceModel(GradleBuild, operationParameters)
        then:
        1 * delegate.produceModel(GradleProject, operationParameters) >> gradleProject
        model instanceof GradleBuild
    }

    def "non GradleBuild model requests passed to delegate"() {
        setup:
        ConsumerOperationParameters operationParameters = Mock(ConsumerOperationParameters)
        SomeModel someModel = new SomeModel()
        when:
        def returnValue = modelProducer.produceModel(SomeModel, operationParameters)
        then:
        1 * delegate.produceModel(SomeModel, operationParameters) >> someModel
        returnValue == someModel
        0 * adapter.adapt(_, _)
    }

    def gradleProject() {
        GradleProject gradleProject = Mock(GradleProject)
        1 * gradleProject.children >> ([] as DomainObjectSet<GradleProject>)
        1 * gradleProject.name >> "SomeProject"
        1 * gradleProject.path >> ":"
        gradleProject
    }

    static class SomeModel {
    }
}

