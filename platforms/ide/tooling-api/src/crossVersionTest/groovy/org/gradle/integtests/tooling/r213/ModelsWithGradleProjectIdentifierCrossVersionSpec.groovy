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

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.ProjectPublications
import spock.lang.Ignore

class ModelsWithGradleProjectIdentifierCrossVersionSpec extends ToolingApiSpecification {
    static List<Class<?>> modelsHavingGradleProjectIdentifier = [BuildInvocations, ProjectPublications]

    TestFile rootSingle
    TestFile rootMulti

    def setup() {
        rootSingle = singleProjectBuild("A")
        rootMulti = multiProjectBuild("B", ['x', 'y'])
    }

    @TargetGradleVersion(">=3.0")
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
    @TargetGradleVersion(">=3.0")
    def "ProjectConnection with custom action provides identified models for multi-project build"() {
        when:
        def gradleProjects = getModelsWithProjectConnection(rootMulti, GradleProject)
        def models = getModelsWithProjectConnection(rootMulti, modelType)

        then:
        assertSameIdentifiers(gradleProjects, models)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    private static void assertSameIdentifiers(def gradleProject, def model) {
        assertSameIdentifiers([gradleProject], [model])
    }

    private static void assertSameIdentifiers(List gradleProjects, List models) {
        def gradleProjectIdentifiers = gradleProjects.collect { it.projectIdentifier } as Set<ProjectIdentifier>
        def modelIdentifiers = models.collect { it.projectIdentifier } as Set<ProjectIdentifier>
        assert gradleProjectIdentifiers*.projectPath == modelIdentifiers*.projectPath
        assert gradleProjectIdentifiers*.buildIdentifier*.rootDir == modelIdentifiers*.buildIdentifier*.rootDir
    }

    private getModelWithProjectConnection(TestFile rootDir, Class modelType = GradleProject, boolean searchUpwards = true) {
        def connector = connector()
        connector.forProjectDirectory(rootDir)
        connector.searchUpwards(searchUpwards)
        return withConnection(connector) { it.getModel(modelType) }
    }

    private getModelsWithProjectConnection(TestFile rootDir, Class modelType = GradleProject, boolean searchUpwards = true) {
        FetchProjectModelsBuildAction buildAction = new FetchProjectModelsBuildAction(modelType)
        def connector = connector()
        connector.forProjectDirectory(rootDir)
        connector.searchUpwards(searchUpwards)
        withConnection(connector) { connection ->
            connection.action(buildAction).run()
        }
    }

    BuildTestFixture getBuildTestFixture() {
        new BuildTestFixture(temporaryFolder).withBuildInSubDir()
    }

    def singleProjectBuild(String projectName, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        buildTestFixture.singleProjectBuild(projectName, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        buildTestFixture.multiProjectBuild(projectName, subprojects, cl)
    }
}
