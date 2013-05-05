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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.CustomModel
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.InternalConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class InternalConnectionBackedConsumerConnectionTest extends Specification {
    final InternalConnection target = Mock() {
        getMetaData() >> Mock(ConnectionMetaDataVersion1)
    }
    final ConsumerOperationParameters parameters = Mock()
    final ProtocolToModelAdapter adapter = Mock()
    final ModelMapping modelMapping = Stub()
    final InternalConnectionBackedConsumerConnection connection = new InternalConnectionBackedConsumerConnection(target, modelMapping, adapter)

    def "describes capabilities of the provider"() {
        given:
        def details = connection.versionDetails

        expect:
        details.supportsConfiguringJavaHome()
        details.supportsConfiguringJvmArguments()
        details.supportsConfiguringStandardInput()
        details.supportsGradleProjectModel()

        and:
        !details.supportsRunningTasksWhenBuildingModel()

        and:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(IdeaProject)
        details.isModelSupported(BasicIdeaProject)
        details.isModelSupported(GradleProject)
        details.isModelSupported(BuildEnvironment)
        details.isModelSupported(Void)

        and:
        !details.isModelSupported(ProjectOutcomes)
        !details.isModelSupported(CustomModel)
    }

    def "builds model using connection's getTheModel() method"() {
        when:
        def result = connection.run(String.class, parameters)

        then:
        result == 'ok'

        and:
        _ * modelMapping.getProtocolType(String.class) >> Integer.class
        1 * target.getTheModel(Integer.class, parameters) >> 12
        1 * adapter.adapt(String.class, 12, _) >> 'ok'
        0 * target._
    }

    def "runs build using connection's executeBuild() method"() {
        when:
        connection.run(Void.class, parameters)

        then:
        1 * target.executeBuild(parameters, parameters)
        0 * target._
    }
}
