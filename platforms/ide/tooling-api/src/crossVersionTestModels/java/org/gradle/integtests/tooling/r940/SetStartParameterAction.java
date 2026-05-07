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
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.Serializable;

class SetStartParameterAction implements BuildAction<String>, Serializable {

    private final boolean resilient;

    SetStartParameterAction(boolean resilient) {
        this.resilient = resilient;
    }

    @Override
    public String execute(BuildController controller) {
        if (resilient) {
            GradleBuild gradleBuild = controller.fetch(GradleBuild.class).getModel();
            if (gradleBuild != null) {
                FetchModelResult<StartParametersModel> result = controller.fetch(gradleBuild.getRootProject(), StartParametersModel.class);
                return result.getFailures().isEmpty() ? "successful" : "unsuccessful";
            }
            return "unsuccessful";
        } else {
            GradleBuild gradleBuild = controller.getModel(GradleBuild.class);
            StartParametersModel result = controller.getModel(gradleBuild.getRootProject(), StartParametersModel.class);
            return result.toString();
        }
    }
}
