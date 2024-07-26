/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.actions;

import org.gradle.api.Action;
import org.gradle.internal.cc.impl.fixtures.SomeToolingModel;
import org.gradle.internal.cc.impl.fixtures.SomeToolingModelParameter;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FetchParameterizedCustomModelForEachProject implements BuildAction<Map<String, List<SomeToolingModel>>> {

    private final List<String> parameters;

    public FetchParameterizedCustomModelForEachProject(List<String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Map<String, List<SomeToolingModel>> execute(BuildController controller) {
        GradleBuild buildModel = controller.getBuildModel();
        Map<String, List<SomeToolingModel>> result = new LinkedHashMap<>();
        for (String parameter : parameters) {
            Map<String, SomeToolingModel> model = fetchSomeModelForAllProjects(controller, buildModel, parameter);
            for (Map.Entry<String, SomeToolingModel> entry : model.entrySet()) {
                result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
        }

        return result;
    }

    private static Map<String, SomeToolingModel> fetchSomeModelForAllProjects(BuildController controller, GradleBuild buildModel, final String parameterValue) {
        Map<String, SomeToolingModel> result = new LinkedHashMap<>();
        for (BasicGradleProject project : buildModel.getProjects()) {
            SomeToolingModel model = fetchSomeModel(controller, project, parameterValue);
            if (model != null) {
                result.put(project.getBuildTreePath(), model);
            }
        }
        return result;
    }

    @SuppressWarnings({"Convert2Lambda", "NullableProblems"})
    private static SomeToolingModel fetchSomeModel(BuildController controller, BasicGradleProject project, String parameter) {
        return controller.findModel(project, SomeToolingModel.class, SomeToolingModelParameter.class, new Action<SomeToolingModelParameter>() {
            @Override
            public void execute(SomeToolingModelParameter customParameter) {
                customParameter.setMessagePrefix(parameter);
            }
        });
    }
}
