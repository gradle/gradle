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

package org.gradle.integtests.tooling.r812;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.Arrays;
import java.util.List;

public class FetchBuildAndProjectModels implements BuildAction<List<Object>> {
    @Override
    public List<Object> execute(BuildController controller) {
        GradleBuild gradleBuild = controller.getBuildModel();
        GradleBuild gradleBuild1 = controller.getModel(gradleBuild, GradleBuild.class);
        GradleProject project = controller.getModel(gradleBuild.getRootProject(), GradleProject.class);
        return Arrays.asList((Object) gradleBuild1, project);
    }
}
