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
import org.gradle.integtests.tooling.fixture.RequiresIntegratedComposite
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.internal.connection.DefaultProjectIdentifier
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

import static org.gradle.util.CollectionUtils.single

/**
 * Dependency substitution is performed for composite build accessed via the `GradleConnection` API.
 */
class ToolingModelDependenciesCompositeCrossVersionSpec extends CompositeToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()
    TestFile buildA
    TestFile buildB
    def builds = []
    def publishedModuleB1
    EclipseProject eclipseProjectA
    EclipseProject eclipseProjectB1
    IdeaModule ideaModuleA
    IdeaModule ideaModuleB1

    def setup() {
        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        publishedModuleB1 = mavenRepo.module("org.test", "b1", "1.0").publish()

        buildA = singleProjectBuild("buildA") {
            buildFile << """
        apply plugin: 'java'
        dependencies {
            compile "org.test:b1:1.0"
        }
        repositories {
            maven { url '${mavenRepo.uri}' }
        }
"""
}
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
        allprojects {
            apply plugin: 'java'
        }
"""
}
        builds << buildA << buildB
    }

    def "EclipseProject model has dependencies substituted in composite"() {
        when:
        loadEclipseProjectModels()

        then:
        if (isIntegratedComposite()) {
            assert eclipseProjectA.classpath.empty
            assert eclipseProjectA.projectDependencies.size() == 1
            with(eclipseProjectA.projectDependencies.first()) {
                assert path == 'b1'
                assert targetProject == null
                assert target == eclipseProjectB1.identifier
            }
        } else {
            assert eclipseProjectA.projectDependencies.empty
            assert eclipseProjectA.classpath.size() == 1
            def externalDependency = eclipseProjectA.classpath.first()
            assert externalDependency.file == publishedModuleB1.artifactFile
        }
    }

    @RequiresIntegratedComposite
    @TargetGradleVersion(">=3.0")
    def "EclipseProject model honours custom project name"() {
        when:
        buildB.buildFile << """
            subprojects {
                apply plugin: 'eclipse'
                eclipse {
                    project.name = project.name + "-renamed"
                }
            }
            project(":b1") {
                dependencies {
                    compile project(":b2")
                }
            }
"""

        def eclipseProjects = loadEclipseProjectModels()
        def eclipseProjectB2 = eclipseProjects.find { it.projectDirectory.absoluteFile == buildB.file('b2').absoluteFile }

        then:
        eclipseProjectA.projectDependencies.size() == 2
        def depB1 = eclipseProjectA.projectDependencies.find { it.target == eclipseProjectB1.identifier }
        depB1 != null
        depB1.path == 'b1-renamed'

        and:
        def depB2 = eclipseProjectA.projectDependencies.find { it.target == eclipseProjectB2.identifier }
        depB2 != null
        depB2.path == 'b2-renamed'

        and:
        def depB1onB2 = single(eclipseProjectB1.projectDependencies)
        depB1onB2.path == 'b2-renamed'
        depB1onB2.target == eclipseProjectB2.identifier
    }

    def "Idea model has dependencies substituted in composite"() {
        when:
        loadIdeaModuleModels()

        then:
        if (isIntegratedComposite()) {
            assert ideaModuleB1.identifier != null
            assert ideaModuleA.dependencies.size() == 1
            with(ideaModuleA.dependencies.first()) {
                assert it instanceof IdeaModuleDependency
                assert dependencyModule == null
                assert target == ideaModuleB1.identifier
            }
        } else {
            assert ideaModuleA.dependencies.size() == 1
            def externalDependency = ideaModuleA.dependencies.first()
            assert externalDependency instanceof IdeaSingleEntryLibraryDependency
            assert externalDependency.file == publishedModuleB1.artifactFile
        }
    }

    @RequiresIntegratedComposite
    def "Idea model honours custom module name"() {
        when:
        buildB.buildFile << """
            subprojects {
                apply plugin: 'idea'
                idea {
                    module.name = module.name + "-renamed"
                }
            }
            project(":b1") {
                dependencies {
                    compile project(":b2")
                }
            }
"""

        def ideaModules = loadIdeaModuleModels()
        def ideaModuleB2 = ideaModules.find { it.gradleProject.projectIdentifier == new DefaultProjectIdentifier(buildB, ":b2") }

        then:
        ideaModuleA.dependencies.size() == 2
        ideaModuleA.dependencies.any { it instanceof IdeaModuleDependency && it.target == ideaModuleB1.identifier }
        ideaModuleA.dependencies.any { it instanceof IdeaModuleDependency && it.target == ideaModuleB2.identifier }

        and:
        ideaModuleB1.dependencies.size() == 1
        ideaModuleA.dependencies.any { it instanceof IdeaModuleDependency && it.target == ideaModuleB2.identifier }
    }

    private ArrayList<EclipseProject> loadEclipseProjectModels() {
        def eclipseProjects = withCompositeConnection(builds) { connection ->
            connection.getModels(EclipseProject).asList()*.model
        }
        assert eclipseProjects.size() == 4
        eclipseProjectA = eclipseProjects.find { it.projectDirectory.absoluteFile == buildA.absoluteFile }
        eclipseProjectB1 = eclipseProjects.find { it.projectDirectory.absoluteFile == buildB.file('b1').absoluteFile }
        assert eclipseProjectA != null
        assert eclipseProjectB1 != null
        return eclipseProjects
    }

    private List<IdeaModule> loadIdeaModuleModels() {
        def ideaProjects = withCompositeConnection(builds) { connection ->
            connection.getModels(IdeaProject).asList()*.model
        }
        def ideaModules = ideaProjects*.modules.flatten() as List<IdeaModule>
        assert ideaModules.size() == 4
        ideaModuleA = ideaModules.find { it.gradleProject.projectIdentifier == new DefaultProjectIdentifier(buildA, ":") }
        ideaModuleB1 = ideaModules.find { it.gradleProject.projectIdentifier == new DefaultProjectIdentifier(buildB, ":b1") }
        assert ideaModuleA != null
        assert ideaModuleB1 != null
        ideaModules
    }
}
