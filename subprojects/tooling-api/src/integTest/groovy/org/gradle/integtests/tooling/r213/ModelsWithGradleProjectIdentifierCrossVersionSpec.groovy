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

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.util.GradleVersion
import spock.lang.Ignore

class ModelsWithGradleProjectIdentifierCrossVersionSpec extends CompositeToolingApiSpecification {
    TestFile rootSingle
    TestFile rootMulti

    void setup() {
        rootSingle = singleProjectBuild("A")
        rootMulti = multiProjectBuild("B", ['x', 'y'])
    }

    def "GradleConnection provides identified models for single project build"() {
        when:
        def gradleProjects = getModelsWithGradleConnection([rootSingle], EclipseProject)*.gradleProject
        def models = getModelsWithGradleConnection([rootSingle], modelType)

        then:
        gradleProjects.size() == 1
        models.size() == 1
        assertSameIdentifiers(gradleProjects[0], models[0])

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    def "GradleConnection provides identified models for multi-project build"() {
        when:
        def gradleProjects = getModelsWithGradleConnection([rootMulti], EclipseProject)*.gradleProject
        def models = getModelsWithGradleConnection([rootMulti], modelType)

        then:
        gradleProjects.size() == models.size()
        assertSameIdentifiers(gradleProjects, models)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    def "GradleConnection provides identified models for composite build"() {
        when:
        def gradleProjects = getModelsWithGradleConnection([rootMulti, rootSingle], EclipseProject)*.gradleProject
        def models = getModelsWithGradleConnection([rootMulti, rootSingle], modelType)

        then:
        gradleProjects.size() == models.size()
        assertSameIdentifiers(gradleProjects, models)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    def "all Launchables are identified when obtained from GradleConnection"() {
        when:
        def buildInvocationsSet = getModelsWithGradleConnection([rootMulti, rootSingle], BuildInvocations)

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

    @TargetGradleVersion(">=2.13")
    def "ProjectConnection provides identified models for single project build"() {
        when:
        def gradleProject = getModelWithProjectConnection(rootSingle, GradleProject)
        def model = getModelWithProjectConnection(rootSingle, modelType)

        then:
        assertSameIdentifiers(gradleProject, model)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    @Ignore("Test fails sporadically")
    @TargetGradleVersion(">=2.13")
    def "ProjectConnection with custom action provides identified models for multi-project build"() {
        when:
        def gradleProjects = getModelsWithProjectConnection(rootMulti, GradleProject)
        def models = getModelsWithProjectConnection(rootMulti, modelType)

        then:
        assertSameIdentifiers(gradleProjects, models)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    @TargetGradleVersion('>=1.2 <1.12')
    def "decent error message for Gradle version that doesn't expose publications"() {
        when:
        def modelResults = withCompositeConnection([rootMulti, rootSingle]) { GradleConnection connection ->
            def modelBuilder = connection.models(ProjectPublications)
            modelBuilder.get()
        }.asList()

        then:
        modelResults.size() == 2
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

    private List getModelsWithGradleConnection(List<TestFile> rootDirs, Class modelType) {
        withCompositeConnection(rootDirs) { GradleConnection connection ->
            def modelBuilder = connection.models(modelType)
            modelBuilder.get()
        }.asList()*.model
    }

    private getModelWithProjectConnection(TestFile rootDir, Class modelType = GradleProject, boolean searchUpwards = true) {
        GradleConnector connector = connector()
        connector.forProjectDirectory(rootDir)
        ((DefaultGradleConnector) connector).searchUpwards(searchUpwards)
        return withConnection(connector) { it.getModel(modelType) }
    }

    private getModelsWithProjectConnection(TestFile rootDir, Class modelType = GradleProject, boolean searchUpwards = true) {
        FetchProjectModelsBuildAction buildAction = new FetchProjectModelsBuildAction(modelType)
        GradleConnector connector = connector()
        connector.forProjectDirectory(rootDir)
        ((DefaultGradleConnector) connector).searchUpwards(searchUpwards)
        withConnection(connector) { connection ->
            connection.action(buildAction).run()
        }
    }

    private static getModelsHavingGradleProjectIdentifier() {
        List<Class<?>> models = [BuildInvocations]
        if (targetDistVersion >= GradleVersion.version("1.12")) {
            models += ProjectPublications
        }
        return models
    }
}
