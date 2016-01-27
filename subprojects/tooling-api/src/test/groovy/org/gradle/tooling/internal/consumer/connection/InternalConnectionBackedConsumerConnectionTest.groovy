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

import org.gradle.tooling.BuildAction
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
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
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class InternalConnectionBackedConsumerConnectionTest extends Specification {
    final ConnectionMetaDataVersion1 metaData = Stub() {
        getVersion() >> '1.0-milestone-8'
    }
    final InternalConnection target = Mock() {
        getMetaData() >> metaData
    }
    final ConsumerOperationParameters parameters = Mock()
    final ProtocolToModelAdapter adapter = Mock()
    final ModelMapping modelMapping = Stub()
    final InternalConnectionBackedConsumerConnection connection = new InternalConnectionBackedConsumerConnection(target, modelMapping, adapter)

    def "describes capabilities of the provider"() {
        given:
        def details = connection.versionDetails

        expect:
        !details.supportsTaskDisplayName()
        !details.supportsCancellation()

        and:
        details.maySupportModel(HierarchicalEclipseProject)
        details.maySupportModel(EclipseProject)
        details.maySupportModel(IdeaProject)
        details.maySupportModel(BasicIdeaProject)
        details.maySupportModel(GradleProject)
        details.maySupportModel(BuildEnvironment)
        details.maySupportModel(Void)

        and:
        !details.maySupportModel(ProjectOutcomes)
        !details.maySupportModel(CustomModel)
        !details.maySupportModel(GradleBuild)
        !details.maySupportModel(BuildInvocations)
    }

    def "builds GradleBuild model by converting GradleProject"() {
        def model = Stub(GradleBuild.class)
        def gradleProject = Stub(GradleProject.class)
        when:
        def result = connection.run(GradleBuild.class, parameters)
        then:
        result == model
        and:
        _ * modelMapping.getProtocolType(GradleProject.class) >> GradleProject.class

        1 * target.getTheModel(GradleProject.class, parameters) >> gradleProject
        1 * adapter.adapt(GradleProject.class, gradleProject, _) >> gradleProject
        1 * adapter.adapt(GradleBuild.class, _) >> model
        0 * target._
    }

    def "builds model using connection's getTheModel() method"() {
        def model = Stub(GradleProject)

        when:
        def result = connection.run(GradleProject.class, parameters)

        then:
        result == model

        and:
        _ * modelMapping.getProtocolType(GradleProject.class) >> Integer.class
        1 * target.getTheModel(Integer.class, parameters) >> 12
        1 * adapter.adapt(GradleProject.class, 12, _) >> model
        0 * target._
    }

    def "runs build using connection's executeBuild() method"() {
        when:
        connection.run(Void.class, parameters)

        then:
        1 * target.executeBuild(parameters, parameters)
        0 * target._
    }

    def "fails when unknown model is requested"() {
        when:
        connection.run(CustomModel.class, parameters)

        then:
        UnknownModelException e = thrown()
        e.message == /The version of Gradle you are using (1.0-milestone-8) does not support building a model of type 'CustomModel'. Support for building custom tooling models was added in Gradle 1.6 and is available in all later versions./
    }

    def "fails when both tasks and model requested"() {
        given:
        parameters.tasks >> ['a']
        parameters.entryPointName >> "<api>"

        when:
        connection.run(GradleProject.class, parameters)

        then:
        UnsupportedOperationConfigurationException e = thrown()
        e.message == /The version of Gradle you are using (1.0-milestone-8) does not support the <api> forTasks() configuration option. Support for this is available in Gradle 1.2 and all later versions./
    }

    def "fails when arguments specified with 1.0-m8"() {
        given:
        parameters.arguments >> ['-thing']
        parameters.entryPointName >> "<api>"

        when:
        connection.run(GradleProject.class, parameters)

        then:
        UnsupportedOperationConfigurationException e = thrown()
        e.message == /The version of Gradle you are using (1.0-milestone-8) does not support the <api> withArguments() configuration option. Support for this is available in Gradle 1.0 and all later versions./
    }

    def "fails when build action requested"() {
        given:
        parameters.tasks >> ['a']
        parameters.entryPointName >> "<api>"

        when:
        connection.run(Stub(BuildAction), parameters)

        then:
        UnsupportedVersionException e = thrown()
        e.message == /The version of Gradle you are using (1.0-milestone-8) does not support the <api>. Support for this is available in Gradle 1.8 and all later versions./
    }
}
