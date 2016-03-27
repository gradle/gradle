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

    TestFile setup() {
        rootSingle = singleProjectJavaBuild("A")
        rootMulti = multiProjectJavaBuild("B", ['x', 'y'])
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
                buildInvocations.gradleProjectIdentifier == it.gradleProjectIdentifier
            }
            buildInvocations.tasks.each {
                buildInvocations.gradleProjectIdentifier == it.gradleProjectIdentifier
            }
        }
    }

    @Ignore("Currently this is all done in the composite coordinator: these models are not identified when accessed from a ProjectConnection")
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

    private static void assertSameIdentifiers(def gradleProject, def model) {
        assert gradleProject.identifier == model.gradleProjectIdentifier
    }

    private static void assertSameIdentifiers(List gradleProjects, List models) {
        def gradleProjectIdentifiers = gradleProjects.collect { it.identifier } as Set
        def modelIdentifiers = models.collect { it.gradleProjectIdentifier } as Set
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

    private static getModelsHavingGradleProjectIdentifier() {
        List<Class<?>> models = [BuildInvocations]
        if (getTargetDistVersion() >= GradleVersion.version("1.12")) {
            models += ProjectPublications
        }
        return models
    }
}
