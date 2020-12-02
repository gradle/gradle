/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject

import static org.gradle.performance.annotations.ScenarioType.TEST
import static org.gradle.performance.generator.JavaTestProjectGenerator.LARGE_MONOLITHIC_JAVA_PROJECT
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = TEST, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"])
)
class JavaIDEModelPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.targetVersions = ["6.9-20201201230040+0000"]
        runner.minimumBaseVersion = "2.11"
    }

    def "get IDE model for Eclipse"() {
        given:
        setupRunner()

        runner.toolingApi("Eclipse model") {
            it.model(EclipseProject)
        }.run { builder ->
            def model = builder.get()
            // we must actually do something to highlight some performance issues
            forEachEclipseProject(model) {
                buildCommands.each {
                    it.name
                    it.arguments
                }
                withGradleProject(gradleProject)
                classpath.collect {
                    [it.exported, it.file, it.gradleModuleVersion.group, it.gradleModuleVersion.name, it.gradleModuleVersion.version, it.javadoc, it.source]
                }
                javaSourceSettings?.jdk?.javaHome
                withJava(javaSourceSettings?.jdk?.javaVersion)
                withJava(javaSourceSettings?.sourceLanguageLevel)
                withJava(javaSourceSettings?.targetBytecodeVersion)
                projectNatures.each {
                    it.id
                }
                projectDependencies.each {
                    it.exported
                    it.path
                }
                description
                name
                linkedResources.each {
                    it.name
                    it.location
                    it.locationUri
                    it.type
                }
                projectDirectory
                sourceDirectories.each {
                    it.path
                    it.directory
                }
            }
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "get IDE model for IDEA"() {
        given:
        setupRunner()
        runner.toolingApi("IDEA model") {
            it.model(IdeaProject)
        }.run { builder ->
            def model = builder.get()
            // we must actually do something to highlight some performance issues
            model.with {
                name
                description
                jdkName
                languageLevel.level
                withJava(javaLanguageSettings.languageLevel)
                withJava(javaLanguageSettings.targetBytecodeVersion)
                withJava(javaLanguageSettings.jdk.javaVersion)
                javaLanguageSettings.jdk.javaHome
                modules.each {
                    it.compilerOutput.inheritOutputDirs
                    it.compilerOutput.outputDir
                    it.compilerOutput.testOutputDir
                    it.contentRoots.each {
                        it.excludeDirectories
                        withIdeaSources(it.generatedSourceDirectories)
                        withIdeaSources(it.generatedTestDirectories)
                        withIdeaSources(it.sourceDirectories)
                        withIdeaSources(it.testDirectories)
                    }
                    it.dependencies.each {
                        it.scope.scope
                        if (ExternalDependency.isAssignableFrom(it.class)) {
                            it.gradleModuleVersion.group
                            it.gradleModuleVersion.name
                            it.gradleModuleVersion.version
                        }
                    }
                    withGradleProject(it.gradleProject)
                }
            }
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    private setupRunner() {
        def iterations = determineIterations()
        runner.warmUpRuns = iterations
        runner.runs = iterations
    }

    private determineIterations() {
        return runner.testProject == LARGE_MONOLITHIC_JAVA_PROJECT.projectName ? 200 : 40
    }

    private static void forEachEclipseProject(def elm, @DelegatesTo(value = EclipseProject) Closure<?> action) {
        action.delegate = elm
        action.call()
        elm.children?.each {
            forEachEclipseProject(it, action)
        }
    }

    private static void withIdeaSources(def sources) {
        sources.each {
            it.generated
            it.directory
        }
    }

    private static void withGradleProject(def gradleProject) {
        gradleProject.buildDirectory
        gradleProject.path
        gradleProject.buildScript.sourceFile
        gradleProject.buildDirectory
        gradleProject.name
        gradleProject.projectDirectory
        gradleProject.description
        gradleProject.tasks.collect {
            it.name
            it.project
            it.path
            it.description
            it.displayName
            it.group
            it.public
        }
    }

    private static void withJava(def it) {
        if (it != null) {
            it.java5
            it.java5Compatible
            it.java6
            it.java6Compatible
            it.java7
            it.java7Compatible
            it.java8Compatible
            it.java9Compatible
        }
    }
}
