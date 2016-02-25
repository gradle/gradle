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
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject

import java.lang.reflect.Proxy

/**
 * Tooling client requests arbitrary model type for every project in a composite
 */
class ArbitraryModelsCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    private static final List<Class<?>> HIERARCHICAL_MODELS = [EclipseProject, HierarchicalEclipseProject, GradleProject]
    private static final List<Class<?>> HIERARCHICAL_SPECIAL_MODELS = [IdeaProject, BasicIdeaProject]
    private static final List<Class<?>> BUILD_MODELS = [BuildEnvironment, GradleBuild]
    private static final List<Class<?>> PROJECT_MODELS = [BuildInvocations, ProjectPublications]
    private static final List<Class<?>> ALL_MODELS = [] + HIERARCHICAL_MODELS + HIERARCHICAL_SPECIAL_MODELS + BUILD_MODELS + PROJECT_MODELS

    def "check that all models are returned for composite"(TestScenario testScenario) {
        given:
        def builds = testScenario.createBuilds(this.&createBuilds)

        when:
        def modelResults = withCompositeConnection(builds) { connection ->
            def modelBuilder = connection.models(testScenario.modelType)
            modelBuilder.get()
        }.asList()
        def models = modelResults*.model

        def implementationClassNames = models.collect {
            // get the name of the implementation class behind the proxy
            unpackProxy(it).getClass().name
        } as Set

        then:
        models.each {
            // this will never fail because of the proxy adapter solution
            assert testScenario.modelType.isInstance(it)
        }

        // check that all returned models are of the same implementation class
        // instanceof checks aren't useful because instances will always implement the requested model type
        implementationClassNames.size() == 1

        // check that we get the expected number of model results
        models.size() == testScenario.expectedNumberOfModelResults

        where:
        testScenario << createTestScenarios(ALL_MODELS)
    }

    private static List<TestScenario> createTestScenarios(List<Class<?>> modelTypes) {
        modelTypes.collect { modelType ->
            [new TestScenario(modelType: modelType, numberOfSingleProjectBuilds: 1),
             new TestScenario(modelType: modelType, numberOfMultiProjectBuilds: 1),
             new TestScenario(modelType: modelType, numberOfSingleProjectBuilds: 1, numberOfMultiProjectBuilds: 1),
            ]
        }.flatten()
    }

    private static class TestScenario {
        Class<?> modelType
        int numberOfSingleProjectBuilds
        int numberOfMultiProjectBuilds
        int numberOfSubProjectsPerMultiProjectBuild = 3

        List<TestFile> createBuilds(Closure<List<TestFile>> createBuilds) {
            createBuilds("single", numberOfSingleProjectBuilds, 0) + createBuilds("multi", numberOfMultiProjectBuilds, numberOfSubProjectsPerMultiProjectBuild)
        }

        int getExpectedNumberOfModelResults() {
            if (modelType == BuildEnvironment) {
                getNumberOfBuilds()
            } else {
                getNumberOfProjects()
            }
        }

        int getNumberOfBuilds() {
            numberOfSingleProjectBuilds + numberOfMultiProjectBuilds
        }

        int getNumberOfProjects() {
            numberOfSingleProjectBuilds + (numberOfMultiProjectBuilds * (numberOfSubProjectsPerMultiProjectBuild + 1))
        }
    }

    private List<TestFile> createBuilds(String prefix, int numberOfBuilds, int numberOfSubProjects) {
        if (numberOfBuilds < 1) {
            return []
        }
        def builds = (1..numberOfBuilds).collect { buildNumber ->
            populate("${prefix}-${buildNumber}") {
                buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
                settingsFile << """
                rootProject.name = '${rootProjectName}'
"""
                if (numberOfSubProjects > 0) {
                    def subProjects = (1..numberOfSubProjects).collect { "${rootProjectName}-${new String([('a' as char) + (it - 1)] as char[])}".toString() }
                    settingsFile << "include '${subProjects.join("', '")}'\n"
                }
            }
        }
        builds
    }

    private Object unpackProxy(Object obj) {
        if (Proxy.isProxyClass(obj.getClass())) {
            def handler = Proxy.getInvocationHandler(obj)
            if (handler.hasProperty("delegate")) {
                return unpackProxy(handler.delegate)
            }
        }
        return obj
    }
}
