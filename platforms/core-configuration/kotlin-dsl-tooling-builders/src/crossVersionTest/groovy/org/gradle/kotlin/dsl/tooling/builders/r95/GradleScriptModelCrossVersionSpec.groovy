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
import org.gradle.tooling.model.buildscript.ComponentSources
import org.gradle.tooling.model.buildscript.ComponentSourcesRequest
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.GradleScriptsModel
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
import org.gradle.tooling.model.buildscript.SourceComponentIdentifier
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.util.GradleVersion

import static org.junit.Assume.assumeTrue

@TargetGradleVersion(">=9.5")
class GradleScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "query script with mapper classpath and sources with sources downloaded in a subsequent call"() {
        given:
        withMultipleSubprojects()

        when:
        def model = loadToolingModel(GradleScriptsModel)

        then:
        model.modelsByScripts.size() == 3
        def projectScript = model.modelsByScripts.keySet().drop(1).first()
        def projectModel = model.modelsByScripts[projectScript]
        def contextPathElement = projectModel.contextPath.first()
        def sourcePath = contextPathElement.sourcePath
        sourcePath.size() == 3
        sourcePath[0] == sourcePath[1]
        sourcePath[0] != sourcePath[2]

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
