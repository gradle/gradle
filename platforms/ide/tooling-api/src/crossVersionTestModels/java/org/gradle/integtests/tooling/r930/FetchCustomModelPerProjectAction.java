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

package org.gradle.integtests.tooling.r930;

import org.gradle.integtests.tooling.r16.CustomModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

class FetchCustomModelPerProjectAction implements BuildAction<Result<Map<String, String>>> {
    @Override
    public Result<Map<String, String>> execute(BuildController controller) {
        FetchModelResult<GradleBuild> gradleBuildResult = controller.fetch(GradleBuild.class, null, null);
        assert gradleBuildResult.getModel() instanceof GradleBuild;
        assert gradleBuildResult.getFailures().isEmpty();
        GradleBuild gradleBuild = gradleBuildResult.getModel();
        List<String> failures = new ArrayList<>();
        List<String> causes = new ArrayList<>();
        Map<String, String> values = new TreeMap<>();
        for (BasicGradleProject project : gradleBuild.getProjects()) {
            FetchModelResult<CustomModel> result = controller.fetch(CustomModel.class, null, null);
            CustomModel model = result.getModel();
            values.put(project.getName(), model != null ? model.getValue() : null);
            failures.addAll(result.getFailures().stream().map(Failure::getMessage).collect(Collectors.toList()));
            causes.addAll(result.getFailures().stream().flatMap(f -> f.getCauses().stream()).map(Failure::getMessage).collect(Collectors.toList()));
        }
        return new Result<>(values, failures, causes);
    }
}
