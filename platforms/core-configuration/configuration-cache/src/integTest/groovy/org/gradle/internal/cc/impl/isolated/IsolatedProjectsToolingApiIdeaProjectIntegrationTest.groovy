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

package org.gradle.internal.cc.impl.isolated

import org.gradle.api.JavaVersion
import org.gradle.plugins.ide.internal.tooling.idea.IsolatedIdeaModuleInternal
import org.gradle.plugins.ide.internal.tooling.model.IsolatedGradleProjectInternal
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaDependency
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.gradle.tooling.provider.model.internal.PluginApplyingBuilder
import org.gradle.util.internal.ToBeImplemented

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkGradleProject
import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkModel
import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkProjectIdentifier

class IsolatedProjectsToolingApiIdeaProjectIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    private def pluginApplyingModel = PluginApplyingBuilder.MODEL_NAME

    def "can fetch IdeaProject model"() {
        settingsFile << """
            rootProject.name = 'root'
        """

        when:
        withIsolatedProjects()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertModelStored {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        then:
        ideaModel.name == "root"

        when:
        withIsolatedProjects()
        fetchModel(IdeaProject)

        then:
        fixture.assertModelLoaded()
    }

    def "can fetch BasicIdeaProject model for root and re-fetch cached"() {
        settingsFile << """
            rootProject.name = 'root'
        """

        when:
        withIsolatedProjects()
        def ideaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertModelStored {
            modelsCreated(":", models(BasicIdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        then:
        ideaModel.name == "root"

        when:
        withIsolatedProjects()
        fetchModel(BasicIdeaProject)

        then:
        fixture.assertModelLoaded()
    }

    def "can fetch IdeaProject model for empty projects"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
            include(":lib1:lib11")
        """

        when: "fetching without Isolated Projects"
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        originalIdeaModel.modules.size() == 3
        originalIdeaModel.modules.every { it.children.isEmpty() } // IdeaModules are always flattened

        when: "fetching with Isolated Projects"
        withIsolatedProjects()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertModelStored {
            // IdeaProject, plugin application "model", intermediate IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            // plugin application "model", intermediate IsolatedGradleProject, IsolatedIdeaModule
            modelsCreated(":lib1", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1:lib11", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        checkIdeaProject(ideaModel, originalIdeaModel)
    }

    def "fetching IdeaProject model for non-root project fails"() {
        settingsFile << """
            rootProject.name = "root"
            include("a")
        """

        when:
        withIsolatedProjects()
        runBuildActionFails(new FetchIdeaProjectForTarget(":a"))

        then:
        fixture.assertNoConfigurationCache()

        failureDescriptionContains("org.gradle.tooling.model.idea.IdeaProject can only be requested on the root project, got project ':a'")
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

        when: "fetching without Isolated Projects"
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        originalIdeaModel.modules.name == ["root", "lib1"]
        originalIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        originalIdeaModel.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_9

        when: "fetching with Isolated Projects"
        withIsolatedProjects()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertModelStored {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
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

        when: "fetching without Isolated Projects"
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        originalIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        originalIdeaModel.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_8
        originalIdeaModel.modules.name == ["root", "lib1", "lib2"]
        originalIdeaModel.modules[1].javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_7
        originalIdeaModel.modules[1].javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7
        originalIdeaModel.modules[2].javaLanguageSettings == null

        when: "fetching with Isolated Projects"
        withIsolatedProjects()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertModelStored {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib2", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
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
        withIsolatedProjects()
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertModelStored {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":a", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":b", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
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
        withIsolatedProjects()
        def updatedModel = fetchModel(IdeaProject)

        then:
        fixture.assertModelUpdated {
            fileChanged("a/build.gradle")
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":a", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsReused(":b")
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

        when: "fetching without Isolated Projects"
        def originalIdeaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        with(originalIdeaModel.children.find { it.name == "impl" }) { impl ->
            impl.dependencies.size() == 1
            def apiDep = impl.dependencies[0] as IdeaModuleDependency
            apiDep.targetModuleName == "api"
        }

        when: "fetching with Isolated Projects"
        withIsolatedProjects()
        def ideaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertModelStored {
            modelsCreated(":", models(BasicIdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":api", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":impl", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
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

        when: "fetching without Isolated Projects"
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


        when: "fetching with Isolated Projects"
        withIsolatedProjects()
        def result = runBuildAction(new FetchAllIdeaProjects())

        then:
        fixture.assertModelStored {
            buildModelCreated()
            modelsCreated(
                [":", ":buildB", ":buildC", ":buildD"],
                models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal)
            )
            modelsCreated(
                [":buildB:b1", ":buildB:b2", ":buildD:b1", ":buildD:buildC"],
                models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal)
            )
        }

        checkModel(result, originalResult, [
            [{ it.allIdeaProjects }, { a, e -> checkIdeaProject(a, e) }]
        ])


        when: "fetching again with Isolated Projects"
        withIsolatedProjects()
        def anotherResult = runBuildAction(new FetchAllIdeaProjects())

        then:
        fixture.assertModelLoaded()

        checkModel(anotherResult, originalResult, [
            [{ it.allIdeaProjects }, { a, e -> checkIdeaProject(a, e) }]
        ])

        when: "fetching after change with Isolated Projects"
        file("buildC/build.gradle") << """
            println("changed root in buildC")
        """
        withIsolatedProjects()
        def afterChangeResult = runBuildAction(new FetchAllIdeaProjects())

        then:
        fixture.assertModelUpdated {
            fileChanged("buildC/build.gradle")
            projectsConfigured(":buildB", ":buildB:b1", ":buildB:b2", ":buildC", ":buildD", ":buildD:b1", ":buildD:buildC")
            modelsCreated(":buildC", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsReused(":", ":buildB", ":buildD")
        }
        outputContains("changed root in buildC")

        checkModel(afterChangeResult, originalResult, [
            [{ it.allIdeaProjects }, { a, e -> checkIdeaProject(a, e) }]
        ])
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/27363")
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

        when: "fetching without Isolated Projects"
        def originalIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        originalIdeaModel.modules.name == ["root", "lib1"]

        when: "fetching with Isolated Projects"
        withIsolatedProjects()
        fetchModelFails(IdeaProject)

        then:
        fixture.assertModelStoredAndDiscarded {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            // TODO:isolated there should be no violation
            problem("Plugin class 'org.gradle.plugins.ide.idea.IdeaPlugin': Project ':lib1' cannot access 'Project.tasks' functionality on another project ':'")
        }

        // TODO:isolated check the model matches the vintage model
//        checkIdeaProject(ideaModel, originalIdeaModel)
    }

    private static void checkIdeaProject(IdeaProject actual, IdeaProject expected) {
        checkModel(actual, expected, [
            { it.parent },
            { it.name },
            { it.description },
            { it.jdkName },
            { it.languageLevel.level },
            [{ it.children }, { a, e -> checkIdeaModule(a, e) }],
        ])
    }

    private static void checkIdeaModule(IdeaModule actualModule, IdeaModule expectedModule) {
        checkModel(actualModule, expectedModule, [
            { it.name },
            [{ it.projectIdentifier }, { a, e -> checkProjectIdentifier(a, e) }],
            [{ it.javaLanguageSettings }, { a, e -> checkLanguageSettings(a, e) }],
            { it.jdkName },
            [{ it.contentRoots }, { a, e -> checkContentRoot(a, e) }],
            [{ it.gradleProject }, { a, e -> checkGradleProject(a, e) }],
            { it.project.languageLevel.level }, // shallow check to avoid infinite recursion
            { it.compilerOutput.inheritOutputDirs },
            { it.compilerOutput.outputDir },
            { it.compilerOutput.testOutputDir },
            [{ it.dependencies }, { a, e -> checkDependency(a, e) }],
        ])
    }

    private static void checkContentRoot(IdeaContentRoot actual, IdeaContentRoot expected) {
        checkModel(actual, expected, [
            { it.rootDirectory },
            { it.excludeDirectories },
        ])
    }

    private static void checkDependency(IdeaDependency actual, IdeaDependency expected) {
        checkModel(actual, expected, [
            { it.scope.scope },
            { it.exported },
        ])

        if (expected instanceof IdeaModuleDependency) {
            checkModel(actual, expected, [
                { it.targetModuleName },
            ])
        }

        if (expected instanceof IdeaSingleEntryLibraryDependency) {
            checkModel(actual, expected, [
                { it.file },
                { it.source },
                { it.javadoc },
                { it.exported },
            ])
        }
    }

    private static void checkLanguageSettings(IdeaJavaLanguageSettings actual, IdeaJavaLanguageSettings expected) {
        checkModel(actual, expected, [
            { it.languageLevel },
            { it.targetBytecodeVersion },
            { it.jdk?.javaVersion },
            { it.jdk?.javaHome },
        ])
    }

    private static List<String> models(Object... models) {
        models.collect { it instanceof String ? it : (it as Class<?>).getName() }
    }
}
