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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.internal.connection.DefaultProjectIdentifier
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject

import static org.gradle.util.CollectionUtils.single

/**
 * Dependency substitution is performed for models in a composite build
 */
@TargetGradleVersion(">=3.2")
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_MULTI_MODEL)
class ToolingModelDependenciesCompositeCrossVersionSpec extends ToolingApiSpecification {
    def stdOut = new ByteArrayOutputStream()
    TestFile buildA
    TestFile buildB
    EclipseProject eclipseProjectA
    EclipseProject eclipseProjectB1
    IdeaModule ideaModuleA
    IdeaModule ideaModuleB1

    def setup() {

        buildA = singleProjectBuildInSubfolder("buildA") {
            buildFile << """
        apply plugin: 'java'
        dependencies {
            compile "org.test:b1:1.0"
        }
"""
}
        buildB = multiProjectBuildInSubFolder("buildB", ['b1', 'b2']) {
            buildFile << """
        allprojects {
            apply plugin: 'java'
        }
"""
}
        includeBuilds(buildA, buildB)
    }

    def "EclipseProject model has dependencies substituted in composite"() {
        when:
        loadEclipseProjectModels()

        then:
        assert eclipseProjectA.classpath.empty
        assert eclipseProjectA.projectDependencies.size() == 1
        with(eclipseProjectA.projectDependencies.first()) {
            it.path == 'b1'
            it.targetProject == null
        }
    }

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

        loadEclipseProjectModels()

        then:
        eclipseProjectA.projectDependencies.size() == 2
        eclipseProjectA.projectDependencies.find { it.path == 'b1-renamed' }

        and:
        eclipseProjectA.projectDependencies.find { it.path == 'b2-renamed' }

        and:
        def depB1onB2 = single(eclipseProjectB1.projectDependencies)
        depB1onB2.path == 'b2-renamed'
    }

    def "Idea model has dependencies substituted in composite"() {
        when:
        loadIdeaModuleModels()

        then:
        ideaModuleA.dependencies.size() == 1
        with(ideaModuleA.dependencies.first()) {
            it instanceof IdeaModuleDependency
            dependencyModule == null
            targetModuleName == ideaModuleB1.name
        }
    }

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
        ideaModuleA.dependencies.any { it instanceof IdeaModuleDependency && it.targetModuleName == ideaModuleB1.name }
        ideaModuleA.dependencies.any { it instanceof IdeaModuleDependency && it.targetModuleName == ideaModuleB2.name }

        and:
        ideaModuleB1.dependencies.size() == 1
        ideaModuleA.dependencies.any { it instanceof IdeaModuleDependency && it.targetModuleName == ideaModuleB2.name }
    }

    private ArrayList<EclipseProject> loadEclipseProjectModels() {
        def eclipseProjects = getUnwrappedModels(EclipseProject)
        assert eclipseProjects.size() == 5
        eclipseProjectA = eclipseProjects.find { it.projectDirectory.absoluteFile == buildA.absoluteFile }
        eclipseProjectB1 = eclipseProjects.find { it.projectDirectory.absoluteFile == buildB.file('b1').absoluteFile }
        assert eclipseProjectA != null
        assert eclipseProjectB1 != null
        return eclipseProjects
    }

    private List<IdeaModule> loadIdeaModuleModels() {
        def ideaProjects = getUnwrappedModels(IdeaProject)
        def ideaModules = ideaProjects*.modules.flatten() as List<IdeaModule>
        assert ideaModules.size() == 5
        ideaModuleA = ideaModules.find { it.gradleProject.projectIdentifier == new DefaultProjectIdentifier(buildA, ":") }
        ideaModuleB1 = ideaModules.find { it.gradleProject.projectIdentifier == new DefaultProjectIdentifier(buildB, ":b1") }
        assert ideaModuleA != null
        assert ideaModuleB1 != null
        ideaModules
    }
}
