/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.isolated;

import org.gradle.configurationcache.fixtures.SomeToolingModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.ArrayList;
import java.util.List;

public class FetchCustomModelForEachProjectInParallel implements BuildAction<List<SomeToolingModel>> {
    @Override
    public List<SomeToolingModel> execute(BuildController controller) {
        List<FetchModelForProject> actions = new ArrayList<>();
        GradleBuild buildModel = controller.getBuildModel();
        for (BasicGradleProject project : buildModel.getProjects()) {
            actions.add(new FetchModelForProject(project));
        }
        return controller.run(actions);
    }

    private static class FetchModelForProject implements BuildAction<SomeToolingModel> {
        private final BasicGradleProject project;

        public FetchModelForProject(BasicGradleProject project) {
            this.project = project;
        }

        @Override
        public SomeToolingModel execute(BuildController controller) {
            return controller.findModel(project, SomeToolingModel.class);
        }
    }
}
