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
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.adapter.ViewBuilder
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.CustomModel
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

class BuildActionRunnerBackedConsumerConnectionTest extends Specification {
    final ConnectionMetaDataVersion1 metaData = Stub() {
        getVersion() >> '1.2'
    }
    final TestBuildActionRunner target = Mock() {
        getMetaData() >> metaData
    }
    final ConsumerOperationParameters parameters = Stub()
    final ModelMapping modelMapping = Stub()
    final ProtocolToModelAdapter adapter = Mock()
    final BuildActionRunnerBackedConsumerConnection connection = new BuildActionRunnerBackedConsumerConnection(target, modelMapping, adapter)

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

        and:
        !details.maySupportModel(GradleBuild)
        !details.maySupportModel(BuildInvocations)
        !details.maySupportModel(CustomModel)
    }

    def "configures connection"() {
        def parameters = Stub(ConnectionParameters)

        when:
        connection.configure(parameters)

        then:
        1 * target.configure(parameters)
        0 * target._
    }

    def "builds model using connection's run() method"() {
        BuildResult<String> result = Stub()
        GradleProject adapted = Stub()
        ViewBuilder<GradleProject> builder = Mock()

        when:
        def model = connection.run(GradleProject.class, parameters)

        then:
        model == adapted

        and:
        _ * modelMapping.getProtocolType(GradleProject.class) >> Integer.class
        1 * target.run(Integer.class, parameters) >> result
        _ * result.model >> 12
        1 * adapter.builder(GradleProject.class) >> builder
        1 * builder.build(12) >> adapted
        0 * target._
    }

    def "builds GradleBuild model by converting GradleProject"() {
        BuildResult<GradleProject> result = Stub()
        ViewBuilder<GradleProject> viewBuilder1 = Mock()
        ViewBuilder<GradleBuild> viewBuilder2 = Mock()
        GradleProject original = Stub()
        GradleProject adapted = Stub()
        GradleBuild adaptedGradleBuild = Stub()

        when:
        def model = connection.run(GradleBuild.class, parameters)
        then:
        model == adaptedGradleBuild

        and:
        _ * modelMapping.getProtocolType(GradleProject.class) >> GradleProject.class
        1 * target.run(GradleProject.class, parameters) >> result
        _ * result.model >> original
        1 * adapter.builder(GradleProject.class) >> viewBuilder1
        1 * viewBuilder1.build(original) >> adapted
        1 * adapter.builder(GradleBuild.class) >> viewBuilder2
        1 * viewBuilder2.build( _) >> adaptedGradleBuild
        0 * target._
    }

    def "fails when unknown model is requested"() {
        when:
        connection.run(CustomModel.class, parameters)

        then:
        def e = thrown UnknownModelException
        e.message == /The version of Gradle you are using (1.2) does not support building a model of type 'CustomModel'. Support for building custom tooling models was added in Gradle 1.6 and is available in all later versions./
    }

    def "fails when build action requested"() {
        given:
        parameters.tasks >> ['a']
        parameters.entryPointName >> "<api>"

        when:
        connection.run(Stub(BuildAction), parameters)

        then:
        def e = thrown UnsupportedVersionException
        e.message == /The version of Gradle you are using (1.2) does not support the <api>. Support for this is available in Gradle 1.8 and all later versions./
    }

    interface TestBuildActionRunner extends ConnectionVersion4, BuildActionRunner, ConfigurableConnection {
    }
}
