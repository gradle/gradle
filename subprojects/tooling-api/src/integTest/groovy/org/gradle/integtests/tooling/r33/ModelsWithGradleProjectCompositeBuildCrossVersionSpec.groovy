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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.integtests.tooling.r213.ModelsWithGradleProjectSpecFixtures
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.internal.DefaultBuildIdentifier
import org.gradle.tooling.model.gradle.GradleBuild

@TargetGradleVersion(ToolingApiVersions.SUPPORTS_MULTI_MODEL)
class ModelsWithGradleProjectCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification implements ModelsWithGradleProjectSpecFixtures {

    def "Provides identified GradleBuild for each build"() {
        setup:
        TestFile rootSingle = singleProjectBuildInSubfolder("A")
        TestFile rootMulti = multiProjectBuildInSubFolder("B", ['x', 'y'])
        includeBuilds(rootMulti, rootSingle)

        when:
        def gradleBuilds = getUnwrappedModels(GradleBuild)

        then:
        gradleBuilds.size() == 3
        gradleBuilds.find { it.buildIdentifier == new DefaultBuildIdentifier(rootSingle) }
        gradleBuilds.find { it.buildIdentifier == new DefaultBuildIdentifier(rootMulti) }
    }

    def "Provides GradleProjects for single project build"() {
        setup:
        singleProjectBuildInRootFolder("A")

        when:
        def gradleProjects = getUnwrappedModels(modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 1
        hasProject(gradleProjects, projectDir, ':', 'A')

        where:
        modelType << projectScopedModels
    }

    def "Provides GradleProjects for multi-project build"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        when:
        def gradleProjects = getUnwrappedModels(modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, projectDir, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, projectDir, ':x', 'x', ':')
        hasChildProject(gradleProjects, projectDir, ':y', 'y', ':')

        where:
        modelType << projectScopedModels
    }

    def "Provides GradleProjects for composite build"() {
        setup:
        TestFile rootSingle = singleProjectBuildInSubfolder("A")
        TestFile rootMulti = multiProjectBuildInSubFolder("B", ['x', 'y'])
        includeBuilds(rootSingle, rootMulti)

        when:
        def gradleProjects = getUnwrappedModels(modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 5
        hasProject(gradleProjects, rootSingle, ':', 'A')
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << projectScopedModels
    }

    static class ModelsWithGradleBuildIdentifierCrossVersionSpec extends MultiModelToolingApiSpecification {

        def "provides identified model for single project build"() {
            setup:
            singleProjectBuildInRootFolder("A")

            when:
            def gradleBuilds = getUnwrappedModels(GradleBuild)
            def models = getUnwrappedModels(modelType)

            then:
            gradleBuilds.size() == 1
            models.size() == 1
            assertSameIdentifiers(gradleBuilds[0], models[0])

            where:
            modelType << modelsHavingGradleBuildIdentifier
        }

        def "provides identified model for multi-project build"() {
            setup:
            multiProjectBuildInRootFolder("B", ['x', 'y'])

            when:
            def gradleBuilds = getUnwrappedModels(GradleBuild)
            def models = getUnwrappedModels(modelType)

            then:
            gradleBuilds.size() == 1
            models.size() == 1
            assertSameIdentifiers(gradleBuilds[0], models[0])

            where:
            modelType << modelsHavingGradleBuildIdentifier
        }

        @TargetGradleVersion(">=3.1")
        def "provides identified model for composite build"() {
            when:
            includeBuilds(singleProjectBuildInSubfolder("A"), multiProjectBuildInSubFolder("B", ['x', 'y']))
            def gradleBuilds = getUnwrappedModels(GradleBuild)
            def models = getUnwrappedModels(modelType)

            then:
            gradleBuilds.size() == models.size()
            assertSameIdentifiers(gradleBuilds, models)

            where:
            modelType << modelsHavingGradleBuildIdentifier
        }

        private static void assertSameIdentifiers(def gradleBuild, def model) {
            assert gradleBuild.buildIdentifier == model.buildIdentifier
        }

        private static void assertSameIdentifiers(List gradleBuilds, List models) {
            def gradleBuildIdentifiers = gradleBuilds.collect { it.buildIdentifier } as Set
            def modelBuildIdentifiers = models.collect { it.buildIdentifier } as Set
            assert gradleBuildIdentifiers == modelBuildIdentifiers
        }

        private static getModelsHavingGradleBuildIdentifier() {
            List<Class<?>> models = [BuildEnvironment]
            return models
        }
    }
}
