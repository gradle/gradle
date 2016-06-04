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
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

/**
 * Dependency substitution is performed for composite build accessed via the `GradleConnection` API.
 */
// TODO:DAZ Need coverage for builds that have customized Idea/Eclipse configuration (including module names)
class ToolingModelDependenciesCompositeCrossVersionSpec extends CompositeToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()
    def buildA
    def buildB
    def builds = []
    def publishedModuleB

    def setup() {
        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        publishedModuleB = mavenRepo.module("org.test", "buildB", "1.0").publish()

        buildA = singleProjectBuild("buildA") {
                    buildFile << """
        apply plugin: 'java'
        dependencies {
            compile "org.test:buildB:1.0"
        }
        repositories {
            maven { url '${mavenRepo.uri}' }
        }
"""
}
        buildB = singleProjectBuild("buildB") {
                    buildFile << """
        apply plugin: 'java'
"""
}
        builds << buildA << buildB
    }

    def "EclipseProject model has dependencies substituted in composite"() {
        when:
        def eclipseProjects = withCompositeConnection(builds) { connection ->
            connection.getModels(EclipseProject).asList()*.model
        }

        then:
        eclipseProjects.size() == 2
        def eclipseProjectA = eclipseProjects.find { it.projectDirectory.absoluteFile == buildA.absoluteFile }
        def eclipseProjectB = eclipseProjects.find { it.projectDirectory.absoluteFile == buildB.absoluteFile }
        eclipseProjectA != null
        eclipseProjectB != null
        if (isIntegratedComposite()) {
            assert eclipseProjectA.classpath.empty
            assert eclipseProjectA.projectDependencies.size() == 1
            with(eclipseProjectA.projectDependencies.first()) {
                assert path == 'buildB'
                assert targetProject == null
                assert target == eclipseProjectB.identifier
            }
        } else {
            assert eclipseProjectA.projectDependencies.empty
            assert eclipseProjectA.classpath.size() == 1
            def externalDependency = eclipseProjectA.classpath.first()
            assert externalDependency.file == publishedModuleB.artifactFile
        }
    }

    def "Idea model has dependencies substituted in composite"() {
        when:
        def ideaProjects = withCompositeConnection(builds) { connection ->
            connection.getModels(IdeaProject).asList()*.model
        }
        def ideaModules = ideaProjects*.modules.flatten() as List<IdeaModule>

        then:
        ideaModules.size() == 2
        def ideaModuleA = ideaModules.find { it.gradleProject.projectIdentifier.buildIdentifier == new DefaultBuildIdentifier(buildA) }
        def ideaModuleB = ideaModules.find { it.gradleProject.projectIdentifier.buildIdentifier == new DefaultBuildIdentifier(buildB) }
        ideaModuleA != null
        ideaModuleB != null
        if (isIntegratedComposite()) {
            assert ideaModuleB.identifier != null
            assert ideaModuleA.dependencies.size() == 1
            with(ideaModuleA.dependencies.first()) {
                assert it instanceof IdeaModuleDependency
                assert dependencyModule == null
                assert target == ideaModuleB.identifier
            }
        } else {
            assert ideaModuleA.dependencies.size() == 1
            def externalDependency = ideaModuleA.dependencies.first()
            assert externalDependency instanceof IdeaSingleEntryLibraryDependency
            assert externalDependency.file == publishedModuleB.artifactFile
        }
    }
}
