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

import org.gradle.integtests.tooling.fixture.GradleConnectionToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier
import org.gradle.tooling.model.gradle.GradleBuild

class ModelsWithGradleProjectViaGradleConnectionCrossVersionSpec extends GradleConnectionToolingApiSpecification implements ModelsWithGradleProjectSpecFixtures {

    TestFile rootSingle
    TestFile rootMulti

    void setup() {
        rootSingle = singleProjectBuild("A")
        rootMulti = multiProjectBuild("B", ['x', 'y'])
    }

    def "Provides identified GradleBuild for each build"() {
        when:
        def gradleBuilds = getUnwrappedModelsWithGradleConnection(includeBuilds(rootMulti, rootSingle), GradleBuild)

        then:
        gradleBuilds.size() == 2
        gradleBuilds.find { it.buildIdentifier == new DefaultBuildIdentifier(rootSingle) }
        gradleBuilds.find { it.buildIdentifier == new DefaultBuildIdentifier(rootMulti) }
    }

    def "Provides GradleProjects for single project build"() {
        when:
        def gradleProjects = getUnwrappedModelsWithGradleConnection(rootSingle, modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 1
        hasProject(gradleProjects, rootSingle, ':', 'A')

        where:
        modelType << projectScopedModels
    }

    def "Provides GradleProjects for multi-project build"() {
        when:
        def gradleProjects = getUnwrappedModelsWithGradleConnection(rootMulti, modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << projectScopedModels
    }

    def "Provides GradleProjects for composite build"() {
        when:
        def gradleProjects = getUnwrappedModelsWithGradleConnection(includeBuilds(rootSingle, rootMulti), modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 4
        hasProject(gradleProjects, rootSingle, ':', 'A')
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << projectScopedModels
    }
}
