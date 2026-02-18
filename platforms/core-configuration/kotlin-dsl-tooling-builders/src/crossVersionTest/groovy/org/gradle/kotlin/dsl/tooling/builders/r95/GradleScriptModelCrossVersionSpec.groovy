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
import org.gradle.kotlin.dsl.tooling.builders.StandardGradleScriptsModel
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.buildscript.ComponentSources
import org.gradle.tooling.model.buildscript.ComponentSourcesRequest
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.GradleScriptsModel
import org.gradle.tooling.model.buildscript.InitScriptsModel
import org.gradle.tooling.model.buildscript.ProjectScriptsModel
import org.gradle.tooling.model.buildscript.SettingsScriptModel
import org.gradle.tooling.model.buildscript.SourceComponentIdentifier
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
        def sourcePath = result.modelsByScripts.values().collectMany {
            it.contextPath.collectMany { it.sourcePath }
        }.unique()

        when:
        def sourcesModel = withConnection {
            it.action(new ComponentSourcesBuildAction(sourcePath))
//                .withArguments("-Dorg.gradle.debug=true")
                .run()
        }

        then:
        println sourcesModel
        sourcesModel != null
    }


    def "query script with mapped classpath and sources with sources downloaded in a subsequent call"() {
        given:
        withMultipleSubprojects()
        settingsFileKts << """
        """

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
        def model = loadToolingModel(GradleScriptsModel)

        then:
        model.modelsByScripts.size() == 4
        def projectScript = model.modelsByScripts.keySet().drop(2).first()
        def projectModel = model.modelsByScripts[projectScript]
        println projectModel.scriptFile
        def contextPathElements = projectModel.contextPath.takeRight(2)
        def sourcePath = contextPathElements.collectMany { it.sourcePath }
        sourcePath.size() == 2
        sourcePath[0] != sourcePath[1]
        // sourcePath[0] != sourcePath[2]

        when:
        def sourcesModel = withConnection {
            it.action(new ComponentSourcesBuildAction(sourcePath))
//                .withArguments("-Dorg.gradle.debug=true")
                .run()
        }

        then:
        sourcesModel != null
        sourcesModel.sourcesByComponents.size() == 2
        def sourceArtifacts = sourcesModel.sourcesByComponents.values().collectMany { it }
        println sourceArtifacts
    }
}

class AllScriptModelsAndSourcesBuildAction implements BuildAction<GradleScriptsModel> {
    @Override
    GradleScriptsModel execute(BuildController controller) {
        def init = controller.getModel(InitScriptsModel)
        def build = controller.getModel(GradleBuild)
        def settings = controller.getModel(build.rootProject, SettingsScriptModel)
        def projects = build.projects.collect { project ->
            controller.getModel(project, ProjectScriptsModel)
        }

        Map<File, GradleScriptModel> all = [:]
        all << init.initScriptModels.collectEntries { [it.scriptFile, it] }
        all[settings.settingsScriptModel.scriptFile] = settings.settingsScriptModel
        projects.each { project ->
            all[project.buildScriptModel.scriptFile] = project.buildScriptModel
            all << project.precompiledScriptModels.collectEntries { [it.scriptFile, it] }
        }

        return new StandardGradleScriptsModel(all)
    }
}

class ComponentSourcesBuildAction implements BuildAction<ComponentSources> {

    List<SourceComponentIdentifier> sourceIds

    ComponentSourcesBuildAction(List<SourceComponentIdentifier> sourceIds) {
        this.sourceIds = sourceIds
    }

    @Override
    ComponentSources execute(BuildController controller) {
        return controller.getModel(ComponentSources, ComponentSourcesRequest) {
            it.sourceComponentIdentifiers = sourceIds
        }
    }
}
