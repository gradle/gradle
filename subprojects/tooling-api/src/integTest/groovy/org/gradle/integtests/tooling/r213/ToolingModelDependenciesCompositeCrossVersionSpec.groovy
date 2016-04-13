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
@TargetGradleVersion(">=2.12")
class ToolingModelDependenciesCompositeCrossVersionSpec extends CompositeToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()
    def buildA
    def buildB
    def builds = []
    def mavenRepo

    def setup() {
        mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.test", "buildB", "1.0").publish()

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
        eclipseProjectA != null
        if (supportsIntegratedComposites()) {
            assert eclipseProjectA.classpath.empty
            assert eclipseProjectA.projectDependencies.size() == 1
            with(eclipseProjectA.projectDependencies.first()) {
                assert path == 'buildB::'
                assert targetProject == null
            }
        } else {
            assert eclipseProjectA.projectDependencies.empty
            assert eclipseProjectA.classpath.size() == 1
            with (eclipseProjectA.classpath.first().gradleModuleVersion) {
                assert group == 'org.test'
                assert name == 'buildB'
                assert version == '1.0'
            }
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
        ideaModuleA != null
        if (supportsIntegratedComposites()) {
            assert ideaModuleA.dependencies.size() == 1
            assert ideaModuleA.dependencies.first() instanceof IdeaModuleDependency
            // TODO:DAZ We'll need to provide a way to correlate to a 'foreign' IdeaModule in a composite
            assert ideaModuleA.dependencies.first().dependencyModule == null
        } else {
            assert ideaModuleA.dependencies.size() == 1
            assert ideaModuleA.dependencies.first() instanceof IdeaSingleEntryLibraryDependency
        }
    }
}
