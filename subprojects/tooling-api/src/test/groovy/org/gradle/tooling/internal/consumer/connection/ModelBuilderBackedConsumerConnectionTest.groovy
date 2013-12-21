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
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.CustomModel
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.*
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class ModelBuilderBackedConsumerConnectionTest extends Specification {
    final metaData = Stub(ConnectionMetaDataVersion1)
    final parameters = Stub(ConsumerOperationParameters)
    final target = Mock(TestModelBuilder) {
        getMetaData() >> metaData
    }
    final adapter = Mock(ProtocolToModelAdapter)
    final modelMapping = Mock(ModelMapping)

    def "describes capabilities of a pre 1.8-rc-1 provider"() {
        given:
        metaData.version >> "1.2"
        def connection = new ModelBuilderBackedConsumerConnection(target, modelMapping, adapter)
        def details = connection.versionDetails

        expect:
        details.supportsGradleProjectModel()

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

        and:
        !details.isModelSupported(GradleBuild)
    }

    def "describes capabilities of a post 1.8-rc-1 provider"() {
        given:
        metaData.version >> "1.8-rc-1"
        def connection = new ModelBuilderBackedConsumerConnection(target, modelMapping, adapter)
        def details = connection.versionDetails

        expect:
        details.supportsGradleProjectModel()

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
        details.isModelSupported(GradleBuild)
    }

    def "maps model type to model identifier"() {
        def modelIdentifier = Stub(ModelIdentifier)

        given:
        metaData.version >> "1.2"
        def connection = new ModelBuilderBackedConsumerConnection(target, modelMapping, adapter)

        when:
        connection.run(GradleProject, parameters)

        then:
        1 * modelMapping.getModelIdentifierFromModelType(GradleProject) >> modelIdentifier
        1 * target.getModel(modelIdentifier, parameters) >> Stub(BuildResult)
    }

    def "maps internal unknown model exception to API exception"() {
        def modelIdentifier = Stub(ModelIdentifier)

        given:
        _ * modelMapping.getModelIdentifierFromModelType(GradleProject) >> modelIdentifier
        _ * target.getModel(modelIdentifier, parameters) >> { throw new InternalUnsupportedModelException() }
        _ * metaData.version >> "1.2"
        def connection = new ModelBuilderBackedConsumerConnection(target, modelMapping, adapter)

        when:
        connection.run(GradleProject, parameters)

        then:
        UnknownModelException e = thrown()
        e.message == /No model of type 'GradleProject' is available in this build./
    }

    def "builds GradleBuild model by converting GradleProject"() {
        def modelIdentifier = Stub(ModelIdentifier)
        def model = Stub(GradleBuild.class)
        def gradleProject = Stub(GradleProject.class)

        given:
        _ * metaData.version >> "1.2"
        _ * modelMapping.getModelIdentifierFromModelType(GradleProject) >> modelIdentifier
        def connection = new ModelBuilderBackedConsumerConnection(target, modelMapping, adapter)

        when:
        def result = connection.run(GradleBuild.class, parameters)

        then:
        result == model

        and:
        1 * target.getModel(modelIdentifier, parameters) >> Stub(BuildResult) { getModel() >> gradleProject }
        1 * adapter.adapt(GradleProject.class, gradleProject) >> gradleProject
        1 * adapter.adapt(GradleBuild.class, _) >> model
        0 * target._
    }

    def "fails when build action requested"() {
        given:
        parameters.tasks >> ['a']
        metaData.version >> "1.2"
        def connection = new ModelBuilderBackedConsumerConnection(target, modelMapping, adapter)

        when:
        connection.run(Stub(BuildAction), parameters)

        then:
        UnsupportedVersionException e = thrown()
        e.message == /The version of Gradle you are using (1.2) does not support execution of build actions provided by the tooling API client. Support for this was added in Gradle 1.8 and is available in all later versions./
    }

    interface TestModelBuilder extends ModelBuilder, ConnectionVersion4, ConfigurableConnection {
    }
}
