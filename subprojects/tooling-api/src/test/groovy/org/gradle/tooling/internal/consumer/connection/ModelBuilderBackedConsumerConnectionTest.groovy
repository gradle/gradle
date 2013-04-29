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
import org.gradle.tooling.internal.consumer.versioning.CustomModel
import org.gradle.tooling.internal.protocol.BuildParameters
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.ConfigurableConnection
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.ModelBuilder
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class ModelBuilderBackedConsumerConnectionTest extends Specification {
    final target = Mock(TestModelBuilder) {
        getMetaData() >> Mock(ConnectionMetaDataVersion1)
    }
    final adapter = Stub(ProtocolToModelAdapter)
    final connection = new ModelBuilderBackedConsumerConnection(target, adapter)

    def "describes capabilities of the provider"() {
        given:
        def details = connection.versionDetails

        expect:
        details.supportsConfiguringJavaHome()
        details.supportsConfiguringJvmArguments()
        details.supportsConfiguringStandardInput()
        details.supportsGradleProjectModel()
        details.supportsRunningTasksWhenBuildingModel()

        and:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(IdeaProject)
        details.isModelSupported(BasicIdeaProject)
        details.isModelSupported(GradleProject)
        details.isModelSupported(BuildEnvironment)
        details.isModelSupported(ProjectOutcomes)
        details.isModelSupported(Void)
        details.isModelSupported(CustomModel)
    }

    def "maps model type to model identifier"() {
        def parameters = Stub(ConsumerOperationParameters)

        when:
        connection.run(modelType, parameters)

        then:
        1 * target.getModel(_, parameters) >> { ModelIdentifier identifier, BuildParameters buildParameters ->
            assert identifier.name == modelName
            return Stub(BuildResult)
        }

        where:
        modelType                  | modelName
        Void                       | ModelIdentifier.NULL_MODEL
        HierarchicalEclipseProject | "org.gradle.tooling.model.eclipse.HierarchicalEclipseProject"
        EclipseProject             | "org.gradle.tooling.model.eclipse.EclipseProject"
        IdeaProject                | "org.gradle.tooling.model.idea.IdeaProject"
        GradleProject              | "org.gradle.tooling.model.GradleProject"
        BasicIdeaProject           | "org.gradle.tooling.model.idea.BasicIdeaProject"
        BuildEnvironment           | "org.gradle.tooling.model.build.BuildEnvironment"
        ProjectOutcomes            | "org.gradle.tooling.model.outcomes.ProjectOutcomes"
        CustomModel                | CustomModel.name
    }

    interface TestModelBuilder extends ModelBuilder, ConnectionVersion4, ConfigurableConnection {
    }
}
