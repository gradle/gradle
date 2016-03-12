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
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject

/**
 * Tests composites with a different Gradle versions.
 * This test creates a composite combining a project for a fixed Gradle version (2.8) with the target gradle version for the test.
 */
class HeterogeneousCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    private final static GradleDistribution GRADLE_2_8 = new ReleasedVersionDistributions().getDistribution("2.8")

    def "retrieve models for composite with heterogeneous Gradle versions"() {
        given:
        GradleDistribution fixedDistribution = GRADLE_2_8
        def project = populate("project") {
            buildFile << "apply plugin: 'java'"
        }
        def fixedDistributionProject = populate("project_fixed") {
            buildFile << "apply plugin: 'java'"
        }

        when:
        println "Testing a composite with ${fixedDistribution.version} and ${targetDist.version}"
        def builder = createCompositeBuilder()
        builder.addBuild(createGradleBuildParticipant(project))

        def fixedDistributionBuild = GradleConnector.newGradleBuildBuilder().forProjectDirectory(fixedDistributionProject).useInstallation(fixedDistribution.gradleHomeDir.absoluteFile).create()
        builder.addBuild(fixedDistributionBuild)

        def connection = builder.build()

        def eclipseProjects = connection.getModels(EclipseProject)
        def buildEnvironments = connection.getModels(BuildEnvironment)

        then:
        eclipseProjects.size() == 2
        buildEnvironments.size() == 2

        cleanup:
        connection?.close()
    }
}
