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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild

class ModelsWithGradleBuildIdentifierCrossVersionSpec extends CompositeToolingApiSpecification {
    TestFile rootSingle
    TestFile rootMulti

    def setup() {
        rootSingle = singleProjectBuild("A")
        rootMulti = multiProjectBuild("B", ['x', 'y'])
    }

    def "GradleConnection provides identified model for single project build"() {
        when:
        def gradleBuilds = getModelsWithGradleConnection([rootSingle], GradleBuild)
        def models = getModelsWithGradleConnection([rootSingle], modelType)

        then:
        gradleBuilds.size() == 1
        models.size() == 1
        assertSameIdentifiers(gradleBuilds[0], models[0])

        where:
        modelType << modelsHavingGradleBuildIdentifier
    }

    def "GradleConnection provides identified model for multi-project build"() {
        when:
        def gradleBuilds = getModelsWithGradleConnection([rootMulti], GradleBuild)
        def models = getModelsWithGradleConnection([rootMulti], modelType)

        then:
        gradleBuilds.size() == 1
        models.size() == 1
        assertSameIdentifiers(gradleBuilds[0], models[0])

        where:
        modelType << modelsHavingGradleBuildIdentifier
    }

    def "GradleConnection provides identified model for composite build"() {
        when:
        def gradleBuilds = getModelsWithGradleConnection([rootMulti, rootSingle], GradleBuild)
        def models = getModelsWithGradleConnection([rootMulti, rootSingle], modelType)

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

    private List getModelsWithGradleConnection(List<TestFile> rootDirs, Class modelType) {
        withCompositeConnection(rootDirs) { GradleConnection connection ->
            def modelBuilder = connection.models(modelType)
            modelBuilder.get()
        }.asList()*.model
    }

    private static getModelsHavingGradleBuildIdentifier() {
        List<Class<?>> models = [BuildEnvironment]
        return models
    }
}
