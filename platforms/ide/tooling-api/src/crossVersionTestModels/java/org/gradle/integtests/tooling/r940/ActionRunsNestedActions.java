/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ActionRunsNestedActions implements BuildAction<ActionRunsNestedActions.Models> {
    @Override
    public Models execute(BuildController controller) {
        GradleBuild buildModel = controller.getBuildModel();
        List<NestedAction> projectActions = new ArrayList<NestedAction>();
        for (BasicGradleProject project : buildModel.getProjects()) {
            projectActions.add(new NestedAction(project));
        }
        List<CustomModel> results = controller.run(projectActions);
        return new Models(controller.getCanQueryProjectModelInParallel(CustomModel.class), results);
    }

    static class NestedAction implements BuildAction<CustomModel> {
        private final BasicGradleProject project;

        public NestedAction(BasicGradleProject project) {
            this.project = project;
        }

        @Override
        public CustomModel execute(BuildController controller) {
            return controller.getModel(project, CustomModel.class);
        }
    }

    public static class Models implements Serializable {
        private final boolean parallel;
        private final List<CustomModel> subs;

        public Models(boolean parallel, Collection<CustomModel> subs) {
            this.parallel = parallel;
            this.subs = new ArrayList<>(subs);
        }

        public boolean isMayRunInParallel() {
            return parallel;
        }

        public List<CustomModel> getSubResults() {
            return subs;
        }
    }

    public interface CustomModel {
        String getPath();
    }
}
