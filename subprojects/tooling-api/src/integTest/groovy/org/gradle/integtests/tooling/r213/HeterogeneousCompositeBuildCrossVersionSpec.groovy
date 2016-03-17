/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.composite.BuildIdentity
import org.gradle.tooling.composite.GradleConnection
import org.gradle.tooling.composite.ModelResult
import org.gradle.tooling.composite.ModelResults
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.util.CollectionUtils
import spock.lang.Ignore

/**
 * Tests composites with a different Gradle versions.
 * This test creates a composite combining a project for a fixed Gradle version (2.8) with the target gradle version for the test.
 */
class HeterogeneousCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    private final static GradleDistribution GRADLE_2_8 = new ReleasedVersionDistributions().getDistribution("2.8")

    def varyingProject
    def varyingBuildIdentity
    def fixedBuildIdentity
    def builder

    def setup() {
        GradleDistribution fixedDistribution = GRADLE_2_8
        varyingProject = populate("project") {
            buildFile << "apply plugin: 'java'"
        }
        def fixedDistributionProject = populate("project_fixed") {
            buildFile << "apply plugin: 'java'"
        }

        println "Testing a composite with ${fixedDistribution.version} and ${targetDist.version}"
        builder = createCompositeBuilder()

        def varyingDistributionParticipant = createGradleBuildParticipant(varyingProject)
        varyingBuildIdentity = varyingDistributionParticipant.toBuildIdentity()
        builder.addBuild(varyingDistributionParticipant)

        def fixedDistributionParticipant = GradleConnector.newGradleBuildBuilder().forProjectDirectory(fixedDistributionProject).useInstallation(fixedDistribution.gradleHomeDir.absoluteFile).create()
        fixedBuildIdentity = fixedDistributionParticipant.toBuildIdentity()
        builder.addBuild(fixedDistributionParticipant)
    }

    def "retrieve models for composite with heterogeneous Gradle versions"() {
        when:
        def connection = builder.build()

        def eclipseProjects = connection.getModels(EclipseProject)
        def buildEnvironments = connection.getModels(BuildEnvironment)

        then:
        eclipseProjects.size() == 2
        buildEnvironments.size() == 2

        cleanup:
        connection?.close()
    }

    @TargetGradleVersion(">=1.0 <1.12")
    def "gets errors for unsupported models for composite with heterogeneous Gradle versions"() {
        when:
        GradleConnection connection = builder.build()

        def modelResults = connection.getModels(ProjectPublications)

        then:
        modelResults.size() == 2
        def varyingResult = findModelResult(modelResults, varyingBuildIdentity)
        varyingResult.failure.message == "The version of Gradle you are using (${targetDistVersion.version}) does not support building a model of type 'ProjectPublications'. Support for building 'ProjectPublications' models was added in Gradle 1.12 and is available in all later versions."

        def fixedResult = findModelResult(modelResults, fixedBuildIdentity)
        fixedResult.failure == null
        fixedResult.model != null
        cleanup:
        connection?.close()
    }

    @TargetGradleVersion(">=1.0 <1.6")
    def "gets errors for unknown models for composite with heterogeneous Gradle versions"() {
        when:
        GradleConnection connection = builder.build()

        def modelResults = connection.getModels(CustomModel)

        then:
        modelResults.size() == 2
        def varyingResult = findModelResult(modelResults, varyingBuildIdentity)
        assertFailure(varyingResult.failure, "The version of Gradle you are using (${targetDistVersion.version}) does not support building a model of type 'CustomModel'. Support for building custom tooling models was added in Gradle 1.6 and is available in all later versions.")

        def fixedResult = findModelResult(modelResults, fixedBuildIdentity)
        assertFailure(fixedResult.failure, "No model of type 'CustomModel' is available in this build.")

        cleanup:
        connection?.close()
    }

    @Ignore("Need to support custom types from the client")
    @TargetGradleVersion(">=1.6")
    def "can retrieve custom models from some participants"() {
        varyingProject.file("build.gradle") <<
                """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

apply plugin: CustomPlugin

class CustomModel implements Serializable {
    String getValue() { 'greetings' }
    Set<CustomThing> getThings() { return [new CustomThing()] }
    Map<String, CustomThing> getThingsByName() { return [thing: new CustomThing()] }
}
class CustomThing implements Serializable {
}
class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        return new CustomModel()
    }
}
class CustomPlugin implements Plugin<Project> {
    @Inject
    CustomPlugin(ToolingModelBuilderRegistry registry) {
        registry.register(new CustomBuilder())
    }

    public void apply(Project project) {
    }
}
"""
        GradleConnection connection = builder.build()
        when:
        def modelResults = connection.getModels(CustomModel)

        then:
        modelResults.size() == 2
        def varyingResult = findModelResult(modelResults, varyingBuildIdentity)
        varyingResult.failure == null
        varyingResult.model.value == 'greetings'
        varyingResult.model.things.find { it instanceof CustomModel.Thing }
        varyingResult.model.thingsByName.thing instanceof CustomModel.Thing

        def fixedResult = findModelResult(modelResults, fixedBuildIdentity)
        assertFailure(fixedResult.failure, "No model of type 'CustomModel' is available in this build.")

    }

    ModelResult findModelResult(ModelResults modelResults, BuildIdentity buildIdentity) {
        CollectionUtils.single(modelResults.findAll { ModelResult modelResult ->
            modelResult.projectIdentity.build.equals(buildIdentity)
        })
    }
}
