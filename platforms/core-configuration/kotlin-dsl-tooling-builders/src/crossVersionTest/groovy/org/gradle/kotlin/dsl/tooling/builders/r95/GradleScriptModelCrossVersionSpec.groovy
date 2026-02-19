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
import org.gradle.tooling.model.buildscript.InitScriptsModel
import org.gradle.tooling.model.buildscript.ProjectScriptsModel
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import org.gradle.tooling.model.buildscript.ScriptComponentSources
import org.gradle.tooling.model.buildscript.ScriptComponentSourcesRequest
import org.gradle.tooling.model.buildscript.SettingsScriptModel
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild

@TargetGradleVersion(">=9.5")
class GradleScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "new model"() {
        given:
        withMultipleSubprojects()
        def scriptDependency = """
            buildscript {
                repositories { gradlePluginPortal() }
                dependencies {
                    classpath("org.nosphere.apache.rat:org.nosphere.apache.rat.gradle.plugin:0.8.1")
                    classpath("net.ltgt.errorprone:net.ltgt.errorprone.gradle.plugin:5.0.0")
                }
            }
        """
        file("build.gradle.kts") << scriptDependency
        file("a/build.gradle.kts") << scriptDependency
        file("b/build.gradle.kts") << scriptDependency

        when:
        def result = withConnection {
            it.action(new AllScriptModelsAndSourcesBuildAction())
//                .withArguments("-Dorg.gradle.debug=true")
                .run()
        }

        then:
        result != null
        println result

        when:
        def sourcesModel = withConnection {
            it.action(new AllComponentSourcesBuildAction(result))
//                .withArguments("-Dorg.gradle.debug=true")
                .run()
        }

        then:
        println sourcesModel
        sourcesModel != null
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

class AllScriptModelsAndSourcesBuildAction implements BuildAction<AllScriptsModel> {
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
        def initSources = controller.getModel(ScriptComponentSources, ScriptComponentSourcesRequest) {
            it.sourceComponentIdentifiers = allScriptsModel.initScriptsModel.initScriptModels.collectMany {
                it.contextPath.collectMany { it.sourcePathIdentifiers }
            }
        }.sourcesByComponents
        def settingsSource = controller.getModel(ScriptComponentSources, ScriptComponentSourcesRequest) {
            it.sourceComponentIdentifiers = allScriptsModel.settingsScriptModel.settingsScriptModel.contextPath.collectMany {
                it.sourcePathIdentifiers
            }
        }.sourcesByComponents
        Map<ScriptComponentSourceIdentifier, List<File>> projectSources = allScriptsModel.projectScriptsModels.entrySet().collectEntries { entry ->
            def project = entry.key
            def scriptsModel = entry.value
            controller.getModel(project, ScriptComponentSources, ScriptComponentSourcesRequest) {
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
