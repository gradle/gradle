/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders.r95

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.InitScriptComponentSources
import org.gradle.tooling.model.buildscript.InitScriptsModel
import org.gradle.tooling.model.buildscript.ProjectScriptComponentSources
import org.gradle.tooling.model.buildscript.ProjectScriptsModel
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import org.gradle.tooling.model.buildscript.ScriptComponentSourcesRequest
import org.gradle.tooling.model.buildscript.SettingsScriptComponentSources
import org.gradle.tooling.model.buildscript.SettingsScriptModel
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild

import java.util.function.Predicate

@TargetGradleVersion(">=9.5")
class GradleScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "new model"() {
        given:
        def initKotlinScript = file("some.init.gradle.kts")
        initKotlinScript << ""
        def initGroovyScript = file("some.init.gradle")
        initGroovyScript << ""

        and:
        withMultipleSubprojects()
        file("build.gradle.kts") << """
            buildscript {
                repositories { gradlePluginPortal() }
                dependencies {
                    classpath("org.nosphere.apache.rat:org.nosphere.apache.rat.gradle.plugin:0.8.1")
                }
            }
            plugins {
                id("net.ltgt.errorprone") version "5.0.0"
            }
        """

        and:
        settingsFileKts << """
            include("grr")
            include("dcl")
        """
        file("grr/build.gradle") << ""
        file("dcl/build.gradle.dcl") << ""


        when:
        def allScriptsModel = withConnection {
            it.action(new AllScriptModelsBuildAction())
                .withArguments("-I", initKotlinScript.absolutePath, "-I", initGroovyScript.absolutePath)
//                .withArguments("-Dorg.gradle.debug=true")
                .run()
        }

        then:
        allScriptsModel != null
        allScriptsModel.initScriptsModel.initScriptModels.size() == 2
        allScriptsModel.initScriptsModel.initScriptModels.each {
            assertContainsBaseDependencies(it)
        }

        and:
        allScriptsModel.settingsScriptModel.settingsScriptModel.scriptFile == settingsFileKts
        !allScriptsModel.settingsScriptModel.settingsScriptModel.implicitImports.isEmpty()
        !allScriptsModel.settingsScriptModel.settingsScriptModel.contextPath.isEmpty()
        assertContainsBaseDependencies(allScriptsModel.settingsScriptModel.settingsScriptModel)

        and:
        allScriptsModel.projectScriptsModels.size() == 5
        Map<String, ProjectScriptsModel> projectScriptModels = allScriptsModel.projectScriptsModels.collectEntries { [it.key.buildTreePath, it.value] }
        def rootModel = projectScriptModels[":"]
        def aModel = projectScriptModels[":a"]
        def bModel = projectScriptModels[":b"]
        def grrModel = projectScriptModels[":grr"]
        def dclModel = projectScriptModels[":dcl"]
        rootModel.buildScriptModel.scriptFile == buildFileKts
        aModel.buildScriptModel.scriptFile == file("a/build.gradle.kts")
        bModel.buildScriptModel.scriptFile == file("b/build.gradle.kts")
        grrModel.buildScriptModel.scriptFile == file("grr/build.gradle")
        dclModel.buildScriptModel.scriptFile == file("dcl/build.gradle.dcl")

        and:
        def allProjectModels = allScriptsModel.projectScriptsModels.values()
        allProjectModels.each {
            assert it.precompiledScriptModels.isEmpty()
            assert !it.buildScriptModel.implicitImports.isEmpty()
            assert !it.buildScriptModel.contextPath.isEmpty()
            assertContainsBaseDependencies(it.buildScriptModel)
            assertContainsExternalDependency(it.buildScriptModel) { it.name.startsWith("creadur-rat-gradle-") }
            assertContainsExternalDependency(it.buildScriptModel) { it.name.startsWith("gradle-errorprone-plugin-") }
        }

        when:
        def allSources = withConnection {
            it.action(new AllComponentSourcesBuildAction(allScriptsModel))
//                .withArguments("-Dorg.gradle.debug=true")
                .run()
        }

        then:
        allSources != null
        !allSources.isEmpty()
        assertContainsSourceOfBaseDependencies(allSources)
        assertContainsSourceOfExternalDependencies(allSources, "creadur-rat-gradle", "gradle-errorprone-plugin")
    }

    private static void assertContainsBaseDependencies(GradleScriptModel model) {
        assertContainsGradleApi(model)
        assertContainsExternalDependency(model) { it.name.startsWith("groovy-dateutil-") }
        assertContainsExternalDependency(model) { it.name.startsWith("kotlin-stdlib-") }
    }

    private static void assertContainsGradleApi(GradleScriptModel model) {
        def gradleApi = model.contextPath.find { it.classPathElement.name.startsWith("gradle-api-") }
        assert gradleApi != null: model.scriptFile
        assert !gradleApi.sourcePathIdentifiers.isEmpty(): model.scriptFile
    }

    private static void assertContainsExternalDependency(GradleScriptModel model, Predicate<File> fileSelector) {
        def element = model.contextPath.find { fileSelector.test(it.classPathElement) }
        assert element != null: model.scriptFile
        assert !element.sourcePathIdentifiers.isEmpty(): model.scriptFile
    }

    private static void assertDoesNotContainExternalDependency(GradleScriptModel model, Predicate<File> fileSelector) {
        def element = model.contextPath.find { fileSelector.test(it.classPathElement) }
        assert element == null: model.scriptFile
    }

    private static void assertContainsSourceOfBaseDependencies(Map<ScriptComponentSourceIdentifier, List<File>> allSources) {
        assertContainsSourceOfGradleApi(allSources)
        assertContainsSourceOfExternalDependencies(allSources, "groovy-dateutil", "kotlin-stdlib",)
    }

    private static void assertContainsSourceOfGradleApi(Map<ScriptComponentSourceIdentifier, List<File>> allSources) {
        def gradleApi = allSources.find { it.key.displayName.startsWith("Gradle API") }
        assert gradleApi != null
        assert !gradleApi.value.isEmpty()
    }

    private static void assertContainsSourceOfExternalDependencies(Map<ScriptComponentSourceIdentifier, List<File>> allSources, String... externalDepNames) {
        for (final def externalDepName in externalDepNames) {
            def external = allSources.find { it.key.displayName.contains(externalDepName) }
            assert external != null: externalDepName
            assert !external.value.isEmpty(): externalDepName
        }
    }
}

class AllScriptsModel implements Serializable {
    final InitScriptsModel initScriptsModel
    final SettingsScriptModel settingsScriptModel
    final Map<BasicGradleProject, ProjectScriptsModel> projectScriptsModels

    AllScriptsModel(InitScriptsModel initScriptsModel, SettingsScriptModel settingsScriptModel, Map<BasicGradleProject, ProjectScriptsModel> projectScriptsModels) {
        this.initScriptsModel = initScriptsModel
        this.settingsScriptModel = settingsScriptModel
        this.projectScriptsModels = projectScriptsModels
    }


    @Override
    String toString() {
        return "AllScriptsModel{" +
            "initScriptsModel=" + initScriptsModel +
            ", settingsScriptModel=" + settingsScriptModel +
            ", projectScriptsModels=" + projectScriptsModels +
            '}';
    }
}

class AllScriptModelsBuildAction implements BuildAction<AllScriptsModel> {
    @Override
    AllScriptsModel execute(BuildController controller) {
        def init = controller.getModel(InitScriptsModel)
        def settings = controller.getModel(SettingsScriptModel)
        def build = controller.getModel(GradleBuild)
        def projects = build.projects.collectEntries { project ->
            [project, controller.getModel(project, ProjectScriptsModel)]
        }

        return new AllScriptsModel(init, settings, projects)
    }
}


class AllComponentSourcesBuildAction implements BuildAction<Map<ScriptComponentSourceIdentifier, List<File>>> {

    AllScriptsModel allScriptsModel

    AllComponentSourcesBuildAction(AllScriptsModel allScriptsModel) {
        this.allScriptsModel = allScriptsModel
    }

    @Override
    Map<ScriptComponentSourceIdentifier, List<File>> execute(BuildController controller) {
        def initSources = controller.getModel(InitScriptComponentSources, ScriptComponentSourcesRequest) {
            it.sourceComponentIdentifiers = allScriptsModel.initScriptsModel.initScriptModels.collectMany {
                it.contextPath.collectMany { it.sourcePathIdentifiers }
            }
        }.sourcesByComponents
        def settingsSource = controller.getModel(SettingsScriptComponentSources, ScriptComponentSourcesRequest) {
            it.sourceComponentIdentifiers = allScriptsModel.settingsScriptModel.settingsScriptModel.contextPath.collectMany {
                it.sourcePathIdentifiers
            }
        }.sourcesByComponents
        Map<ScriptComponentSourceIdentifier, List<File>> projectSources = allScriptsModel.projectScriptsModels.entrySet().collectEntries { entry ->
            def project = entry.key
            def scriptsModel = entry.value
            controller.getModel(project, ProjectScriptComponentSources, ScriptComponentSourcesRequest) {
                it.sourceComponentIdentifiers = scriptsModel.buildScriptModel.contextPath
                    .collectMany { it.sourcePathIdentifiers } +
                    scriptsModel.precompiledScriptModels.collectMany {
                        it.contextPath.collectMany { it.sourcePathIdentifiers }
                    }
            }.sourcesByComponents
        }
        return initSources + settingsSource + projectSources
    }
}
