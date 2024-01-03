/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.tooling.r18;

import org.gradle.integtests.tooling.r16.CustomModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.HashMap;
import java.util.Map;

public class UseGradleBuildToFetchProjectModel implements BuildAction<Map<String, CustomModel>> {
    public Map<String, CustomModel> execute(BuildController controller) {
        GradleBuild gradleBuild = controller.getBuildModel();
        Map<String, CustomModel> projects = new HashMap<String, CustomModel>();
        for (BasicGradleProject project : gradleBuild.getProjects()) {
            projects.put(project.getName(), controller.getModel(project, CustomModel.class));
        }
        return projects;
    }
}
