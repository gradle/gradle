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
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.composite.BuildIdentity
import org.gradle.tooling.composite.GradleConnection
import org.gradle.tooling.composite.ModelResult
import org.gradle.tooling.composite.ModelResults
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.ProjectPublications
/**
 * Tests composites with a different Gradle versions.
 * This test creates a composite combining a project for a fixed Gradle version (2.8) with the target gradle version for the test.
 */
class HeterogeneousCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    private final static GradleDistribution GRADLE_2_8 = new ReleasedVersionDistributions().getDistribution("2.8")

    def varyingBuildIdentity
    def fixedBuildIdentity
    def builder

    def setup() {
        GradleDistribution fixedDistribution = GRADLE_2_8
        def project = populate("project") {
            buildFile << "apply plugin: 'java'"
        }
        def fixedDistributionProject = populate("project_fixed") {
            buildFile << "apply plugin: 'java'"
        }

        println "Testing a composite with ${fixedDistribution.version} and ${targetDist.version}"
        builder = createCompositeBuilder()

        def varyingDistributionParticipant = createGradleBuildParticipant(project)
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

    @TargetGradleVersion("<1.12")
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

    ModelResult findModelResult(ModelResults modelResults, BuildIdentity buildIdentity) {
        def result = modelResults.find { ModelResult modelResult ->
            modelResult.projectIdentity.build.equals(buildIdentity)
        }

        assert result != null

        return result
    }
}
