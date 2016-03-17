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
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion

import java.lang.reflect.Proxy
/**
 * Tooling client requests arbitrary model type for every project in a composite
 */
// TODO:DAZ Need to test multi-project where subproject directory does not exist
class ArbitraryModelsCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    private static final List<Class<?>> HIERARCHICAL_MODELS = [EclipseProject, HierarchicalEclipseProject, GradleProject]
    private static final List<Class<?>> HIERARCHICAL_IDEA_MODELS = [IdeaProject, BasicIdeaProject]
    private static final List<Class<?>> BUILD_MODELS = [BuildEnvironment, GradleBuild]

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
        testScenario << createTestScenarios(supportedModels())
    }

    @TargetGradleVersion("<1.12")
    def "check errors returned for unsupported models in a composite"(TestScenario testScenario) {
        given:
        def builds = testScenario.createBuilds(this.&createBuilds)
        println testScenario

        when:
        def modelResults = withCompositeConnection(builds) { connection ->
            def modelBuilder = connection.models(testScenario.modelType)
            modelBuilder.get()
        }

        then:
        // check that we get the expected number of failures based on total number of participants
        modelResults.size() == testScenario.numberOfBuilds

        modelResults.each {
            assert it.failure.message == "The version of Gradle you are using (${targetDistVersion.version}) does not support building a model of type 'ProjectPublications'. Support for building 'ProjectPublications' models was added in Gradle 1.12 and is available in all later versions."
        }
        where:
        testScenario << createTestScenarios([ ProjectPublications ])
    }

    private static List<TestScenario> createTestScenarios(List<Class<?>> modelsToTest) {
        modelsToTest.collect { modelType ->
            [new TestScenario(modelType: modelType, numberOfSingleProjectBuilds: 1),
             new TestScenario(modelType: modelType, numberOfMultiProjectBuilds: 1),
             new TestScenario(modelType: modelType, numberOfSingleProjectBuilds: 1, numberOfMultiProjectBuilds: 1),
            ]
        }.flatten()
    }

    private static List<Class<?>> supportedModels() {
        List<Class<?>> supportedModels = [] + HIERARCHICAL_MODELS + BUILD_MODELS
        // Need to create a copy of the dist GradleVersion, due to classloader issues
        def targetVersion = getTargetDistVersion()
        if (targetVersion >= GradleVersion.version("1.1")) {
            // Idea models fail to apply with 1.0 on because JavaVersion.current() fails
            supportedModels += HIERARCHICAL_IDEA_MODELS
        }
        supportedModels << BuildInvocations
        if (targetVersion >= GradleVersion.version("1.12")) {
            supportedModels << ProjectPublications
        }
        supportedModels
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
            getNumberOfProjects()
        }

        int getNumberOfBuilds() {
            numberOfMultiProjectBuilds + numberOfSingleProjectBuilds
        }

        int getNumberOfProjects() {
            numberOfSingleProjectBuilds + (numberOfMultiProjectBuilds * (numberOfSubProjectsPerMultiProjectBuild + 1))
        }

        @Override
        String toString() {
            return "Request ${modelType.simpleName} for ${numberOfSingleProjectBuilds} single-project and ${numberOfMultiProjectBuilds} w/ ${numberOfSubProjectsPerMultiProjectBuild} multi-project participants"
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
                    subProjects.each { subProject ->
                        settingsFile << "include '${subProject}'\n"
                        addChildDir(subProject)
                    }
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
