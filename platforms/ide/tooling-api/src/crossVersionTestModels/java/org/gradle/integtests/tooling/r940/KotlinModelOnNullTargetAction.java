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
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.integtests.tooling.r940.KotlinModelAction.queryResilientKotlinDslScriptsModel;

class KotlinModelOnNullTargetAction implements BuildAction<KotlinModel>, Serializable {
    @Override
    public KotlinModel execute(BuildController controller) {
        GradleBuild build = controller.fetch(GradleBuild.class).getModel();
        assert build != null;
        Map<File, KotlinDslScriptModel> scriptModels = new HashMap<>();
        Map<File, Failure> failures = new HashMap<>();
        queryResilientKotlinDslScriptsModel(controller, build, null, scriptModels, failures);
        return new KotlinModel(scriptModels, failures);
    }
}
