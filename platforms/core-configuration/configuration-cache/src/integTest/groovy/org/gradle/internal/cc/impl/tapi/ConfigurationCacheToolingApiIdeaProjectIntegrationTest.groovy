/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.cc.impl.tapi

import org.gradle.api.JavaVersion
import org.gradle.internal.cc.impl.actions.FetchAllIdeaProjects
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkModel
import static org.gradle.internal.cc.impl.fixtures.ToolingApiIdeaModelChecker.checkIdeaProject

class ConfigurationCacheToolingApiIdeaProjectIntegrationTest extends AbstractConfigurationCacheToolingApiIntegrationTest {

    def "can fetch IdeaProject model"() {
        settingsFile << """
            rootProject.name = 'root'
        """

        when:
        withConfigurationCacheForModels()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 1
        }

        then:
        ideaModel.name == "root"

        when:
        withConfigurationCacheForModels()
        fetchModel(IdeaProject)

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch BasicIdeaProject model for root and re-fetch cached"() {
        settingsFile << """
            rootProject.name = 'root'
        """

        when:
        withConfigurationCacheForModels()
        def ideaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 1
        }

        then:
        ideaModel.name == "root"

        when:
        withConfigurationCacheForModels()
        fetchModel(BasicIdeaProject)

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch IdeaProject model for empty projects"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
            include(":lib1:lib11")
        """

        when:
        // no configuration cache
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        originalIdeaModel.modules.size() == 3
        originalIdeaModel.modules.every { it.children.isEmpty() } // IdeaModules are always flattened

        when:
        withConfigurationCacheForModels()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 3
        }

        checkIdeaProject(ideaModel, originalIdeaModel)
    }

    def "can fetch IdeaProject model for java projects"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
        """

        file("lib1/build.gradle") << """
            plugins {
                id 'java'
            }
            java.targetCompatibility = JavaVersion.VERSION_1_9
            java.sourceCompatibility = JavaVersion.VERSION_1_8
        """

        when:
        // no configuration cache
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        originalIdeaModel.modules.name == ["root", "lib1"]
        originalIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        originalIdeaModel.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_9

        when:
        withConfigurationCacheForModels()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 2
        }

        checkIdeaProject(ideaModel, originalIdeaModel)
    }

    def "can fetch IdeaProject model for projects with java and idea plugins"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
            include(":lib2")
        """

        file("lib1/build.gradle") << """
            import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
            plugins {
                id 'java'
                id 'idea'
            }
            idea.module.languageLevel = new IdeaLanguageLevel(7)
            idea.module.targetBytecodeVersion = JavaVersion.VERSION_1_7
            java.targetCompatibility = JavaVersion.VERSION_1_8
            java.sourceCompatibility = JavaVersion.VERSION_1_8
        """

        file("lib2/build.gradle") << """
            import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
            plugins {
                id 'idea'
            }
            idea.module.languageLevel = new IdeaLanguageLevel(21)
            idea.module.targetBytecodeVersion = JavaVersion.VERSION_21
        """

        when:
        // no configuration cache
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        originalIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        originalIdeaModel.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_8
        originalIdeaModel.modules.name == ["root", "lib1", "lib2"]
        originalIdeaModel.modules[1].javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_7
        originalIdeaModel.modules[1].javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7
        originalIdeaModel.modules[2].javaLanguageSettings == null

        when:
        withConfigurationCacheForModels()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 3
        }

        checkIdeaProject(ideaModel, originalIdeaModel)
    }

    def "IdeaProject model is invalidated when a child project configuration changes"() {
        settingsFile << """
            rootProject.name = 'root'
            include("a")
            include("b")
        """

        file("a/build.gradle") << """
            plugins {
                id 'java'
            }
            java.sourceCompatibility = JavaVersion.VERSION_1_8
        """
        file("b/build.gradle") << """
            plugins {
                id 'java'
            }
            java.sourceCompatibility = JavaVersion.VERSION_1_9
        """

        when:
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()

        and:
        originalIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_9


        when:
        withConfigurationCacheForModels()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 3
        }

        checkIdeaProject(ideaModel, originalIdeaModel)


        when:
        file("a/build.gradle") << """
            java.sourceCompatibility = JavaVersion.VERSION_11
        """
        def originalUpdatedModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()

        and:
        originalUpdatedModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_11

        when:
        withConfigurationCacheForModels()
        def updatedModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateRecreated {
            fileChanged("a/build.gradle")
            projectConfigured = 3
        }

        and:
        checkIdeaProject(updatedModel, originalUpdatedModel)
    }

    def "can fetch BasicIdeaProject model without resolving external dependencies"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":api")
            include(":impl")
        """

        file("api/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        file("impl/build.gradle") << """
            plugins {
                id 'java'
                id 'idea'
            }

            dependencies {
                implementation(project(":api"))
                testImplementation("i.dont:Exist:2.4")
            }
        """

        when:
        // no configuration cache
        def originalIdeaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        with(originalIdeaModel.children.find { it.name == "impl" }) { impl ->
            impl.dependencies.size() == 1
            def apiDep = impl.dependencies[0] as IdeaModuleDependency
            apiDep.targetModuleName == "api"
        }

        when:
        withConfigurationCacheForModels()
        def ideaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 3
        }

        checkIdeaProject(ideaModel, originalIdeaModel)
    }

    // This test mostly reproduces `org.gradle.plugins.ide.tooling.r31.PersistentCompositeDependencySubstitutionCrossVersionSpec.ensures unique name for all Idea modules in composite`
    def "ensures unique name for all Idea modules in composite"() {
        singleProjectBuildInRootDir("buildA") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    testImplementation "org.test:b1:1.0"

                    testImplementation "org.test:buildC:1.0"
                    testImplementation "org.buildD:b1:1.0"
                }
            """
            settingsFile << """
                includeBuild 'buildB'
                includeBuild 'buildC'
                includeBuild 'buildD'
            """
        }

        multiProjectBuildInSubDir("buildB", ['b1', 'b2']) {
            buildFile << """
                apply plugin: 'java'
            """
            project("b1").buildFile << """
                apply plugin: 'java'
                dependencies {
                    testImplementation "org.test:buildC:1.0"
                }
            """
            project("b2").buildFile << """
                apply plugin: 'java'
            """
        }

        singleProjectBuildInSubDir("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }

        multiProjectBuildInSubDir("buildD", ["b1", "buildC"]) {
            buildFile << """
                apply plugin: 'java'
                group = 'org.buildD'
            """

            ["b1", "buildC"].each {
                project(it).buildFile << """
                    apply plugin: 'java'
                    group = 'org.buildD'
                """
            }
        }

        when:
        // no configuration cache
        def originalResult = runBuildAction(new FetchAllIdeaProjects())

        then:
        originalResult.allIdeaProjects.name == ['buildA', 'buildB', 'buildC', 'buildD']
        originalResult.rootIdeaProject.name == 'buildA'
        originalResult.rootIdeaProject.modules.name == ['buildA']

        def moduleA = originalResult.rootIdeaProject.modules[0]
        moduleA.dependencies.each {
            assert it instanceof IdeaModuleDependency
        }
        moduleA.dependencies.targetModuleName == ['buildB-b1', 'buildA-buildC', 'buildD-b1']

        originalResult.getIdeaProject('buildB').modules.name == ['buildB', 'buildB-b1', 'b2']
        originalResult.getIdeaProject('buildC').modules.name == ['buildA-buildC']
        originalResult.getIdeaProject('buildD').modules.name == ['buildD', 'buildD-b1', 'buildD-buildC']


        when:
        withConfigurationCacheForModels()
        def result = runBuildAction(new FetchAllIdeaProjects())

        then:
        fixture.assertStateStored {
            projectConfigured = 8
        }

        checkModel(result, originalResult, [
            [{ it.allIdeaProjects }, { a, e -> checkIdeaProject(a, e) }]
        ])


        when:
        withConfigurationCacheForModels()
        def anotherResult = runBuildAction(new FetchAllIdeaProjects())

        then:
        fixture.assertStateLoaded()

        checkModel(anotherResult, originalResult, [
            [{ it.allIdeaProjects }, { a, e -> checkIdeaProject(a, e) }]
        ])

        when:
        file("buildC/build.gradle") << """
            println("changed root in buildC")
        """
        withConfigurationCacheForModels()
        def afterChangeResult = runBuildAction(new FetchAllIdeaProjects())

        then:
        fixture.assertStateRecreated {
            fileChanged("buildC/build.gradle")
            projectConfigured = 8
        }
        outputContains("changed root in buildC")

        checkModel(afterChangeResult, originalResult, [
            [{ it.allIdeaProjects }, { a, e -> checkIdeaProject(a, e) }]
        ])
    }

    def "can fetch IdeaProject model for Scala projects"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
        """

        file("lib1/build.gradle") << """
            plugins {
                id 'scala'
            }
        """

        when:
        // no configuration cache
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        originalIdeaModel.modules.name == ["root", "lib1"]

        when:
        withConfigurationCacheForModels()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 2
        }

        checkIdeaProject(ideaModel, originalIdeaModel)
    }

}
