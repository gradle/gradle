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

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.util.GradleVersion
import spock.lang.Ignore

class ModelsWithGradleProjectIdentifierCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=2.13")
    def "ProjectConnection provides identified models for single project build"() {
        setup:
        singleProjectBuildInRootFolder("A")

        when:
        def gradleProject = loadToolingModel(modelType)
        def model = loadToolingModel(modelType)

        then:
        assertSameIdentifiers(gradleProject, model)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    @Ignore("Test fails sporadically")
    @TargetGradleVersion(">=2.13")
    def "ProjectConnection with custom action provides identified models for multi-project build"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        when:
        def gradleProjects = getModelsWithProjectConnectionAndCustomAction(GradleProject)
        def models = getModelsWithProjectConnectionAndCustomAction(modelType)

        then:
        assertSameIdentifiers(gradleProjects, models)

        where:
        modelType << modelsHavingGradleProjectIdentifier
    }

    private static void assertSameIdentifiers(def gradleProject, def model) {
        assert gradleProject.projectIdentifier == model.projectIdentifier
    }

    private getModelsWithProjectConnectionAndCustomAction(Class modelType) {
        FetchProjectModelsBuildAction buildAction = new FetchProjectModelsBuildAction(modelType)
        withConnection { connection -> connection.action(buildAction).run() }
    }

    private static getModelsHavingGradleProjectIdentifier() {
        List<Class<?>> models = [BuildInvocations]
        if (targetDist.getVersion() >= GradleVersion.version("1.12")) {
            models += ProjectPublications
        }
        return models
    }
}
