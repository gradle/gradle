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
import org.gradle.tooling.internal.adapter.ViewBuilder
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.*
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class ModelBuilderBackedConsumerConnectionTest extends Specification {
    final metaData = Stub(ConnectionMetaDataVersion1) {
        getVersion() >> "1.6"
    }
    final parameters = Stub(ConsumerOperationParameters)
    final target = Mock(TestModelBuilder) {
        getMetaData() >> metaData
    }
    final adapter = Mock(ProtocolToModelAdapter)
    final modelMapping = Mock(ModelMapping)
    def connection = new ModelBuilderBackedConsumerConnection(target, modelMapping, adapter)

    def "describes capabilities of the provider"() {
        given:
        def details = connection.versionDetails

        expect:
        !details.supportsCancellation()

        and:
        details.maySupportModel(HierarchicalEclipseProject)
        details.maySupportModel(EclipseProject)
        details.maySupportModel(IdeaProject)
        details.maySupportModel(BasicIdeaProject)
        details.maySupportModel(GradleProject)
        details.maySupportModel(BuildEnvironment)
        details.maySupportModel(ProjectOutcomes)
        details.maySupportModel(Void)
        details.maySupportModel(CustomModel)

        and:
        !details.maySupportModel(GradleBuild)
        !details.maySupportModel(BuildInvocations)
    }

    def "maps model type to model identifier"() {
        def modelIdentifier = Stub(ModelIdentifier)

        when:
        connection.run(GradleProject, parameters)

        then:
        1 * modelMapping.getModelIdentifierFromModelType(GradleProject) >> modelIdentifier
        1 * target.getModel(modelIdentifier, parameters) >> Stub(BuildResult)
        _ * adapter.builder(_) >> Stub(ViewBuilder)
    }

    def "maps internal unknown model exception to API exception"() {
        def modelIdentifier = Stub(ModelIdentifier)

        given:
        _ * modelMapping.getModelIdentifierFromModelType(GradleProject) >> modelIdentifier
        _ * target.getModel(modelIdentifier, parameters) >> { throw new InternalUnsupportedModelException() }

        when:
        connection.run(GradleProject, parameters)

        then:
        def e = thrown UnknownModelException
        e.message == /No model of type 'GradleProject' is available in this build./
    }

    def "builds GradleBuild model by converting GradleProject"() {
        def modelIdentifier = Stub(ModelIdentifier)
        def model = Stub(GradleBuild.class)
        def gradleProject = Stub(GradleProject.class)
        def viewBuilder1 = Mock(ViewBuilder.class)
        def viewBuilder2 = Mock(ViewBuilder.class)

        given:
        _ * modelMapping.getModelIdentifierFromModelType(GradleProject) >> modelIdentifier

        when:
        def result = connection.run(GradleBuild.class, parameters)

        then:
        result == model

        and:
        1 * target.getModel(modelIdentifier, parameters) >> Stub(BuildResult) { getModel() >> gradleProject }
        1 * adapter.builder(GradleProject.class) >> viewBuilder1
        1 * viewBuilder1.build(gradleProject) >> gradleProject
        1 * adapter.builder(GradleBuild.class) >> viewBuilder2
        1 * viewBuilder2.build(_) >> model
        0 * target._
    }

    def "fails when build action requested"() {
        given:
        parameters.tasks >> ['a']
        parameters.entryPointName >> "<api>"

        when:
        connection.run(Stub(BuildAction), parameters)

        then:
        def e = thrown UnsupportedVersionException
        e.message == /The version of Gradle you are using (1.6) does not support the <api>. Support for this is available in Gradle 1.8 and all later versions./
    }

    interface TestModelBuilder extends ModelBuilder, ConnectionVersion4, ConfigurableConnection {
    }

    static class CustomModel {

    }
}
