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

package org.gradle.performance.java

import org.gradle.performance.AbstractToolingApiCrossVersionPerformanceTest
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject
import spock.lang.Unroll

class ToolingApiIdeModelCrossVersionPerformanceTest extends AbstractToolingApiCrossVersionPerformanceTest {

    @Unroll
    def "building Eclipse model for a #template project"() {
        given:

        experiment(template, "get $template EclipseProject model") {
            action {
                def model = model(tapiClass(EclipseProject))
                    .setJvmArguments(customizeJvmOptions(["-Xms$maxMemory", "-Xmx$maxMemory"])).get()
                // we must actually do something to highlight some performance issues
                forEachEclipseProject(model) {
                    if (hasProperty("buildCommands")) {
                        buildCommands.each {
                            it.name
                            it.arguments
                        }
                    }
                    withGradleProject(gradleProject)
                    classpath.collect {
                        [it.exported, it.file, it.gradleModuleVersion.group, it.gradleModuleVersion.name, it.gradleModuleVersion.version, it.javadoc, it.source]
                    }
                    if (hasProperty("javaSourceSettings")) {
                        javaSourceSettings?.jdk?.javaHome
                        withJava(javaSourceSettings?.jdk?.javaVersion)
                        withJava(javaSourceSettings?.sourceLanguageLevel)
                        withJava(javaSourceSettings?.targetBytecodeVersion)
                    }
                    if (hasProperty("projectNatures")) {
                        projectNatures.each {
                            it.id
                        }
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
        template            | maxMemory
        "smallOldJava"      | '128m'
        "mediumOldJava"     | '128m'
        "bigOldJava"        | '704m'
        "lotDependencies"   | '256m'
    }

    @Unroll
    def "building IDEA model for a #template project"() {
        given:
        experiment(template, "get $template IdeaProject model") {
            action {
                def model = model(tapiClass(IdeaProject))
                    .setJvmArguments(customizeJvmOptions(["-Xms$maxMemory", "-Xmx$maxMemory"])).get()
                // we must actually do something to highlight some performance issues
                model.with {
                    name
                    description
                    jdkName
                    languageLevel.level
                    if (hasProperty("javaLanguageSettings")) {
                        withJava(javaLanguageSettings?.languageLevel)
                        withJava(javaLanguageSettings?.targetBytecodeVersion)
                        withJava(javaLanguageSettings?.jdk?.javaVersion)
                        javaLanguageSettings?.jdk?.javaHome
                    }
                    modules.each {
                        it.compilerOutput.inheritOutputDirs
                        it.compilerOutput.outputDir
                        it.compilerOutput.testOutputDir
                        it.contentRoots.each {
                            it.excludeDirectories
                            if (it.hasProperty("generatedSourceDirectories")) { withIdeaSources(it.generatedSourceDirectories) }
                            if (it.hasProperty("generatedTestDirectories")) { withIdeaSources(it.generatedTestDirectories) }
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
        template            | maxMemory
        "smallOldJava"      | '128m'
        "mediumOldJava"     | '128m'
        "bigOldJava"        | '576m'
        "lotDependencies"   | '256m'
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
            if (it.hasProperty("generated")) { it.generated }
            it.directory
        }
    }

    private static void withGradleProject(def gradleProject) {
        if (gradleProject.hasProperty("buildDirectory")) {
            gradleProject.buildDirectory
            gradleProject.buildScript.sourceFile
        }
        gradleProject.path
        gradleProject.name
        if (gradleProject.hasProperty("projectDirectory")) { gradleProject.projectDirectory }
        gradleProject.description
        gradleProject.tasks.collect {
            it.name
            it.project
            it.path
            it.description
            if (it.hasProperty("displayName")) { it.displayName }
            if (it.hasProperty("group")) { it.group }
            if (it.hasProperty("public")) { it.public }
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
