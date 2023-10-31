/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

public class FetchGradleProjectForNonRoot implements BuildAction<GradleProject> {

    private final String targetProject;

    public FetchGradleProjectForNonRoot(String targetProject) {
        this.targetProject = targetProject;
    }

    @Override
    public GradleProject execute(BuildController controller) {
        GradleBuild buildModel = controller.getBuildModel();
        for (BasicGradleProject project : buildModel.getProjects()) {
            if (targetProject.equals(project.getBuildTreePath())) {
                return controller.getModel(project, GradleProject.class);
            }
        }
        return null;
    }
}
