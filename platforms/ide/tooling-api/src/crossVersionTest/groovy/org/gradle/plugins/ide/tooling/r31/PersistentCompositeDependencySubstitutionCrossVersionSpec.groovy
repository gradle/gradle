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

package org.gradle.plugins.ide.tooling.r31

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject

/**
 * Dependency substitution is performed for models in a composite build
 */
@TargetGradleVersion(">=3.1")
class PersistentCompositeDependencySubstitutionCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    TestFile buildA
    TestFile buildB
    TestFile buildC

    def setup() {

        buildA = singleProjectBuildInRootFolder("buildA") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    ${testImplementationConfiguration} "org.test:b1:1.0"
                }
            """
            settingsFile << """
                includeBuild 'buildB'
                includeBuild 'buildC'
            """
        }

        buildB = multiProjectBuildInSubFolder("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
                project(':b1') {
                    dependencies {
                        ${testImplementationConfiguration} "org.test:buildC:1.0"
                    }
                }
            """
        }

        buildC = singleProjectBuildInSubfolder("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }
    }

    def "EclipseProject model has dependencies substituted in composite"() {
        when:
        def eclipseProject = loadToolingModel(EclipseProject)

        then:
        assert eclipseProject.classpath.empty
        eclipseProject.projectDependencies.collect {it.path}  == ['b1']
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
                    ${implementationConfiguration} project(":b2")
                }
            }
"""

        def eclipseProject = loadToolingModel(EclipseProject)

        then:
        eclipseProject.projectDependencies.collect {it.path}.sort()  == ['b1-renamed', 'b2-renamed']
    }

    def "Idea model has dependencies substituted in composite"() {
        when:
        def ideaModule = loadToolingModel(IdeaProject).modules[0]

        then:
        ideaModule.dependencies.size() == 1
        with(ideaModule.dependencies.first()) {
            it instanceof IdeaModuleDependency
            targetModuleName == "b1"
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
                    ${implementationConfiguration} project(":b2")
                }
            }
"""

        def ideaModule = loadToolingModel(IdeaProject).modules[0]

        then:
        ideaModule.dependencies.size() == 2
        ideaModule.dependencies.any { it instanceof IdeaModuleDependency && it.targetModuleName == "b1-renamed" }
        ideaModule.dependencies.any { it instanceof IdeaModuleDependency && it.targetModuleName == "b2-renamed" }

    }

    @TargetGradleVersion(">=3.3")
    def "Idea models for included builds have dependencies substituted"() {
        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }

        then:
        allProjects.rootIdeaProject.modules.size() == 1

        def moduleA = allProjects.rootIdeaProject.modules[0]
        moduleA.dependencies.each {
            assert it instanceof  IdeaModuleDependency
        }
        moduleA.dependencies.collect { it.targetModuleName } == ['b1']

        and:
        assert allProjects.includedBuildIdeaProjects.size() == 2
        def gradleBuildB = allProjects.includedBuildIdeaProjects.keySet().find { it.rootProject.name == 'buildB' }

        gradleBuildB.buildIdentifier.rootDir == buildB

        def projectB = allProjects.includedBuildIdeaProjects.get(gradleBuildB)
        projectB.modules*.name == ['buildB', 'b1', 'b2']

        def moduleB1 = projectB.modules.find {it.name == 'b1'}
        moduleB1.dependencies.collect { it.targetModuleName } == ['buildC']

        and:
        def gradleBuildC = allProjects.includedBuildIdeaProjects.keySet().find { it.buildIdentifier.rootDir == buildC }

        gradleBuildC.rootProject.name == 'buildC'

        def projectC = allProjects.includedBuildIdeaProjects.get(gradleBuildC)
        projectC.modules.size() == 1
        projectC.modules[0].name == 'buildC'
    }

    @TargetGradleVersion(">=4.0")
    def "ensures unique name for all Idea modules in composite"() {
        given:
        buildA.buildFile << """
            dependencies {
                ${testImplementationConfiguration} "org.test:buildC:1.0"
                ${testImplementationConfiguration} "org.buildD:b1:1.0"
            }
"""
        def buildD = multiProjectBuildInSubFolder("buildD", ["b1", "buildC"]) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'

                    group = 'org.buildD'
                }
"""
        }
        settingsFile << """
            includeBuild 'buildD'
"""

        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }

        then:
        allProjects.allIdeaProjects.collect { it.name } == ['buildA', 'buildB', 'buildC', 'buildD']

        // This is not really correct: the IdeaProject for including build should contain all IDEA modules
        // However, it appears that IDEA 2017 depends on this behaviour, and iterates over the included builds to get all modules
        allProjects.rootIdeaProject.name == 'buildA'
        allProjects.rootIdeaProject.modules.collect { it.name } == ['buildA']

        def moduleA = allProjects.rootIdeaProject.modules[0]
        moduleA.dependencies.each {
            assert it instanceof  IdeaModuleDependency
        }
        moduleA.dependencies.collect { it.targetModuleName } == ['buildB-b1', 'buildA-buildC', 'buildD-b1']

        allProjects.getIdeaProject('buildB').modules.collect { it.name } == ['buildB', 'buildB-b1', 'b2']
        allProjects.getIdeaProject('buildC').modules.collect { it.name } == ['buildA-buildC']
        allProjects.getIdeaProject('buildD').modules.collect { it.name } == ['buildD', 'buildD-b1', 'buildD-buildC']
    }

    @TargetGradleVersion(">=3.3")
    def "Does not execute tasks for included builds when generating IDE models"() {
        when:
        def modelOperation = withModel(modelType)
        def modelInstance = modelOperation.model

        then:
        modelType.isInstance modelInstance
        modelOperation.result.assertTasksExecuted()

        where:
        modelType << [EclipseProject, IdeaProject]
    }
}
