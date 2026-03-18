/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r68;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.ArrayList;
import java.util.List;

public class ActionRunsNestedActions implements BuildAction<Models> {
    @Override
    public Models execute(BuildController controller) {
        GradleBuild buildModel = controller.getBuildModel();
        List<GetProjectModel> projectActions = new ArrayList<GetProjectModel>();
        for (BasicGradleProject project : buildModel.getProjects()) {
            projectActions.add(new GetProjectModel(project));
        }
        List<CustomModel> results = controller.run(projectActions);
        return new Models(controller.getCanQueryProjectModelInParallel(CustomModel.class), results);
    }

    static class GetProjectModel implements BuildAction<CustomModel> {
        private final BasicGradleProject project;

        public GetProjectModel(BasicGradleProject project) {
            this.project = project;
        }

        @Override
        public CustomModel execute(BuildController controller) {
            return controller.getModel(project, CustomModel.class);
        }
    }
}
