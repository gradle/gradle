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

import org.gradle.performance.AbstractToolingApiCrossVersionPerformanceTest
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

class JavaIDEModelPerformanceTest extends AbstractToolingApiCrossVersionPerformanceTest {

    private static final BASELINE_VERSION = "6.2-20200114230021+0000"

    @Unroll
    def "get IDE model on #testProject for Eclipse"() {
        given:
        experiment(testProject.projectName) {
            minimumBaseVersion = "2.11"
            targetVersions = [BASELINE_VERSION]
            invocationCount = iterations
            warmUpCount = iterations
            action {
                def model = model(tapiClass(EclipseProject)).setJvmArguments("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").get()
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
        }

        when:
        def results = performMeasurements()

        then:
        results.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | iterations
        LARGE_MONOLITHIC_JAVA_PROJECT | 200
        LARGE_JAVA_MULTI_PROJECT      | 40
    }

    @Unroll
    def "get IDE model on #testProject for IDEA"() {
        given:
        experiment(testProject.projectName) {
            minimumBaseVersion = "2.11"
            targetVersions = [BASELINE_VERSION]
            invocationCount = iterations
            warmUpCount = iterations
            action {
                def model = model(tapiClass(IdeaProject)).setJvmArguments("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").get()
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
                            if (tapiClass(ExternalDependency).isAssignableFrom(it.class)) {
                                it.gradleModuleVersion.group
                                it.gradleModuleVersion.name
                                it.gradleModuleVersion.version
                            }
                        }
                        withGradleProject(it.gradleProject)
                    }
                }
            }
        }

        when:
        def results = performMeasurements()

        then:
        results.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | iterations
        LARGE_MONOLITHIC_JAVA_PROJECT | 200
        LARGE_JAVA_MULTI_PROJECT      | 40
    }

    private static void forEachEclipseProject(def elm, @DelegatesTo(value=EclipseProject) Closure<?> action) {
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
