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
import java.util.Arrays;
import java.util.List;

public class ActionRunsMultipleLevelsOfNestedActions implements BuildAction<List<Models>> {
    @Override
    public List<Models> execute(BuildController controller) {
        GradleBuild buildModel = controller.getBuildModel();
        List<GetModelViaNestedAction> projectActions = new ArrayList<GetModelViaNestedAction>();
        for (BasicGradleProject project : buildModel.getProjects()) {
            projectActions.add(new GetModelViaNestedAction(project));
        }
        return controller.run(projectActions);
    }

    static class GetModelViaNestedAction implements BuildAction<Models> {
        private final BasicGradleProject project;

        public GetModelViaNestedAction(BasicGradleProject project) {
            this.project = project;
        }

        @Override
        public Models execute(BuildController controller) {
            List<CustomModel> models = controller.run(Arrays.asList(
                new ActionRunsNestedActions.GetProjectModel(project),
                new ActionRunsNestedActions.GetProjectModel(project),
                new ActionRunsNestedActions.GetProjectModel(project),
                new ActionRunsNestedActions.GetProjectModel(project),
                new ActionRunsNestedActions.GetProjectModel(project)
            ));
            return new Models(controller.getCanQueryProjectModelInParallel(CustomModel.class), models);
        }
    }
}
