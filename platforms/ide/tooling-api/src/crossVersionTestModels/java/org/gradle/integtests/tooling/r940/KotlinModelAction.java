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

package org.gradle.integtests.tooling.r940;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel;
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class KotlinModelAction implements BuildAction<KotlinModel>, Serializable {

    public enum QueryStrategy {
        ROOT_PROJECT_FIRST,
        INCLUDED_BUILDS_FIRST
    }

    final KotlinModelAction.QueryStrategy queryStrategy;
    final boolean resilient;

    KotlinModelAction(KotlinModelAction.QueryStrategy queryStrategy, boolean resilient) {
        this.queryStrategy = queryStrategy;
        this.resilient = resilient;
    }

    @Override
    public KotlinModel execute(BuildController controller) {
        GradleBuild rootBuild;
        if (resilient) {
            rootBuild = controller.fetch(GradleBuild.class).getModel();
        } else {
            rootBuild = controller.getModel(GradleBuild.class);
        }
        Map<File, KotlinDslScriptModel> scriptModels = new HashMap<>();
        Map<File, Failure> failures = new HashMap<>();

        if (queryStrategy == KotlinModelAction.QueryStrategy.ROOT_PROJECT_FIRST) {
            queryKotlinDslScriptsModel(controller, rootBuild, scriptModels, failures);
            for (GradleBuild build : rootBuild.getEditableBuilds()) {
                queryKotlinDslScriptsModel(controller, build, scriptModels, failures);
            }
        } else if (queryStrategy == KotlinModelAction.QueryStrategy.INCLUDED_BUILDS_FIRST) {
            for (GradleBuild build : rootBuild.getEditableBuilds()) {
                queryKotlinDslScriptsModel(controller, build, scriptModels, failures);
            }
            queryKotlinDslScriptsModel(controller, rootBuild, scriptModels, failures);
        }

        return new KotlinModel(scriptModels, failures);
    }

    private void queryKotlinDslScriptsModel(BuildController controller, GradleBuild build, Map<File, KotlinDslScriptModel> scriptModels, Map<File, Failure> failures) {
        if (resilient) {
            queryResilientKotlinDslScriptsModel(controller, build, build.getRootProject(), scriptModels, failures);
        } else {
            queryBasicKotlinDslScriptsModel(controller, build, scriptModels);
        }
    }

    public static void queryResilientKotlinDslScriptsModel(BuildController controller, GradleBuild build, Model target, Map<File, KotlinDslScriptModel> scriptModels, Map<File, Failure> failures) {
        FetchModelResult<KotlinDslScriptsModel> modelResult = controller.fetch(target, KotlinDslScriptsModel.class);

        assert modelResult.getFailures().size() <= 1: "Expected a single failure, but got multiple ones";
        Optional<? extends Failure> failure = modelResult.getFailures().stream().findAny();
        if (failure.isPresent()) {
            failures.put(build.getBuildIdentifier().getRootDir(), failure.get());
        }

        if (modelResult.getModel() != null) {
            scriptModels.putAll(modelResult.getModel().getScriptModels());
        }
    }

    private static void queryBasicKotlinDslScriptsModel(BuildController controller, GradleBuild build, Map<File, KotlinDslScriptModel> scriptModels) {
        KotlinDslScriptsModel buildScriptModel = controller.getModel(build.getRootProject(), KotlinDslScriptsModel.class);
        scriptModels.putAll(buildScriptModel.getScriptModels());
    }
}
