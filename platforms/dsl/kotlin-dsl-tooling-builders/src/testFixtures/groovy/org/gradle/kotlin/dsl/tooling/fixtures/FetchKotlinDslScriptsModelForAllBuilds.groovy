/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.fixtures

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

/**
 * Fetches {@link org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel} for the root build and all editable builds,
 * mirroring what the IDE does during sync.
 */
class FetchKotlinDslScriptsModelForAllBuilds implements BuildAction<Map<String, KotlinDslScriptsModel>>, Serializable {

    @Override
    Map<String, KotlinDslScriptsModel> execute(BuildController controller) {
        GradleBuild rootBuild = controller.getModel(GradleBuild.class)
        Map<String, KotlinDslScriptsModel> result = [:]
        List<FetchKotlinDslScriptsModelForIncludedBuild> fetchIncludedBuildActions = rootBuild
            .getEditableBuilds()
            .collect { new FetchKotlinDslScriptsModelForIncludedBuild(it) }
        result[rootBuild.rootProject.buildTreePath] = controller.getModel(rootBuild.rootProject, KotlinDslScriptsModel.class)
        result.putAll(controller.run(fetchIncludedBuildActions).collectEntries())
        return result
    }

    private static class FetchKotlinDslScriptsModelForIncludedBuild implements BuildAction<Tuple2<String, KotlinDslScriptsModel>> {

        private final GradleBuild gradleBuild;

        private FetchKotlinDslScriptsModelForIncludedBuild(GradleBuild gradleBuild) { this.gradleBuild = gradleBuild; }

        @Override
        Tuple2<String, KotlinDslScriptsModel> execute(BuildController controller) {
            return new Tuple2(gradleBuild.rootProject.buildTreePath, controller.getModel(gradleBuild.getRootProject(), KotlinDslScriptsModel.class))
        }
    }
}
