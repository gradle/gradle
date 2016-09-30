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

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.util.GradleVersion

class ModelsWithGradleProjectIdentifierCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification {

    def "Provides identified models for single project build"() {
        setup:
        singleProjectBuildInRootFolder("A")

        when:
        def gradleProjects = getUnwrappedModels(EclipseProject)*.gradleProject
        def models = getUnwrappedModels(modelType)

        then:
        gradleProjects.size() == 1
        models.size() == 1
        assertSameIdentifiers(gradleProjects[0], models[0])

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    def "Provides identified models for multi-project build"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        when:
        def gradleProjects = getUnwrappedModels(EclipseProject)*.gradleProject
        def models = getUnwrappedModels(modelType)

        then:
        gradleProjects.size() == models.size()
        assertSameIdentifiers(gradleProjects, models)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    @TargetGradleVersion(">=3.1")
    def "Provides identified models for composite build"() {
        setup:
        includeBuilds(singleProjectBuildInSubfolder("A"), multiProjectBuildInSubFolder("B", ['x', 'y']))

        when:
        def gradleProjects = getUnwrappedModels(EclipseProject)*.gradleProject
        def models = getUnwrappedModels(modelType)

        then:
        gradleProjects.size() == models.size()
        assertSameIdentifiers(gradleProjects, models)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    @TargetGradleVersion(">=3.1")
    def "all Launchables are identified when obtained from a composite"() {
        setup:
        includeBuilds(singleProjectBuildInSubfolder("A"), multiProjectBuildInSubFolder("B", ['x', 'y']))

        when:
        def buildInvocationsSet = getUnwrappedModels(BuildInvocations)

        then:
        buildInvocationsSet.each { BuildInvocations buildInvocations ->
            buildInvocations.taskSelectors.each {
                buildInvocations.projectIdentifier == it.projectIdentifier
            }
            buildInvocations.tasks.each {
                buildInvocations.projectIdentifier == it.projectIdentifier
            }
        }
    }

    @TargetGradleVersion('>=1.2 <1.12')
    def "decent error message for Gradle version that doesn't expose publications"() {
        setup:
        singleProjectBuildInRootFolder("A")

        when:
        def modelResults = withConnection { connection ->
            def modelBuilder = connection.models(ProjectPublications)
            modelBuilder.get()
        }.asList()

        then:
        modelResults.size() == 1
        modelResults.each {
            def e = it.failure
            assert e.message.contains('does not support building a model of type \'ProjectPublications\'.')
            assert e.message.contains('Support for building \'ProjectPublications\' models was added in Gradle 1.12 and is available in all later versions.')
        }
    }

    private static void assertSameIdentifiers(def gradleProject, def model) {
        assert gradleProject.projectIdentifier == model.projectIdentifier
    }

    private static void assertSameIdentifiers(List gradleProjects, List models) {
        def gradleProjectIdentifiers = gradleProjects.collect { it.projectIdentifier } as Set
        def modelIdentifiers = models.collect { it.projectIdentifier } as Set
        assert gradleProjectIdentifiers == modelIdentifiers
    }

    private static getModelsHavingGradleProjectIdentifier() {
        List<Class<?>> models = [BuildInvocations]
        def targetVersion = GradleVersion.version(targetDist.version.baseVersion.version)
        if (targetVersion >= GradleVersion.version("1.12")) {
            models += ProjectPublications
        }
        return models
    }
}
