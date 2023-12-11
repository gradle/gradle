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

package org.gradle.configurationcache.isolated

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
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import org.gradle.tooling.provider.model.internal.PluginApplyingBuilder
import spock.lang.Ignore

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
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        then:
        ideaModel.name == "root"

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModel(IdeaProject)

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch BasicIdeaProject model for root and re-fetch cached"() {
        settingsFile << """
            rootProject.name = 'root'
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", models(BasicIdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        then:
        ideaModel.name == "root"

        when:
        executer.withArguments(ENABLE_CLI)
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

        when: "fetching without Isolated Projects"
        def expectedIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        expectedIdeaModel.modules.size() == 3
        expectedIdeaModel.modules.every { it.children.isEmpty() } // IdeaModules are always flattened

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            // IdeaProject, plugin application "model", intermediate IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            // plugin application "model", intermediate IsolatedGradleProject, IsolatedIdeaModule
            modelsCreated(":lib1", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1:lib11", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        checkIdeaProject(ideaModel, expectedIdeaModel)
    }

    def "can fetch IdeaProject model for non-root project"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
        """

        when: "fetching without Isolated Projects"
        def expectedIdeaModel = runBuildAction(new FetchIdeaProjectForTarget(":lib1"))

        then:
        fixture.assertNoConfigurationCache()

        // Returned model is for root project even though the target is not the root
        expectedIdeaModel.name == "root"
        expectedIdeaModel.modules.name == ["root", "lib1"]

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = runBuildAction(new FetchIdeaProjectForTarget(":lib1"))

        then:
        fixture.assertStateStored {
            buildModelCreated()
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        checkIdeaProject(ideaModel, expectedIdeaModel)
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
            java.targetCompatibility = JavaVersion.VERSION_1_8
            java.sourceCompatibility = JavaVersion.VERSION_1_8
        """

        when: "fetching without Isolated Projects"
        def expectedIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        expectedIdeaModel.modules.name == ["root", "lib1"]
        expectedIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        expectedIdeaModel.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_8

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        checkIdeaProject(ideaModel, expectedIdeaModel)
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
        def expectedIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        expectedIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        expectedIdeaModel.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_8
        expectedIdeaModel.modules.name == ["root", "lib1", "lib2"]
        expectedIdeaModel.modules[1].javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_7
        expectedIdeaModel.modules[1].javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7
        expectedIdeaModel.modules[2].javaLanguageSettings == null

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib2", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        checkIdeaProject(ideaModel, expectedIdeaModel)
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
        def expectedIdeaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        with(expectedIdeaModel.children.find { it.name == "impl" }) { impl ->
            impl.dependencies.size() == 1
            def apiDep = impl.dependencies[0] as IdeaModuleDependency
            apiDep.targetModuleName == "api"
        }

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", models(BasicIdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":api", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":impl", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        checkIdeaProject(ideaModel, expectedIdeaModel)
    }

    // TODO: fix before merge
    @Ignore
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
        def expectedIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        expectedIdeaModel.modules.name == ["root", "lib1"]

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", models(IdeaProject, pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
            modelsCreated(":lib1", models(pluginApplyingModel, IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal))
        }

        checkIdeaProject(ideaModel, expectedIdeaModel)
    }

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
        def expectedResult = runBuildAction(new FetchAllIdeaProjects())

        then:
        expectedResult.allIdeaProjects.name == ['buildA', 'buildB', 'buildC', 'buildD']

        // This is not really correct: the IdeaProject for including build should contain all IDEA modules
        // However, it appears that IDEA 2017 depends on this behaviour, and iterates over the included builds to get all modules
        expectedResult.rootIdeaProject.name == 'buildA'
        expectedResult.rootIdeaProject.modules.name == ['buildA']

        def moduleA = expectedResult.rootIdeaProject.modules[0]
        moduleA.dependencies.each {
            assert it instanceof IdeaModuleDependency
        }
        moduleA.dependencies.targetModuleName == ['buildB-b1', 'buildA-buildC', 'buildD-b1']

        expectedResult.getIdeaProject('buildB').modules.name == ['buildB', 'buildB-b1', 'b2']
        expectedResult.getIdeaProject('buildC').modules.name == ['buildA-buildC']
        expectedResult.getIdeaProject('buildD').modules.name == ['buildD', 'buildD-b1', 'buildD-buildC']

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def result = runBuildAction(new FetchAllIdeaProjects())

        then:
        fixture.assertStateStored {
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

        checkModel(result, expectedResult, [
            [{ ImmutableDomainObjectSet.of(it.allIdeaProjects) }, { a, e -> checkIdeaProject(a, e) }]
        ])

        when: "fetching again with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def anotherResult = runBuildAction(new FetchAllIdeaProjects())

        then:
        fixture.assertStateLoaded()

        checkModel(anotherResult, expectedResult, [
            [{ ImmutableDomainObjectSet.of(it.allIdeaProjects) }, { a, e -> checkIdeaProject(a, e) }]
        ])
    }

    // TODO: add a step that changes on project (that gets incrementally reconfigured) and re-fetch the model

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
