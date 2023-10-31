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
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.gradle.IsolatedGradleProject;

import java.util.ArrayList;
import java.util.List;

public class FetchIsolatedGradleProjectForEachProjectInBuild implements BuildAction<List<IsolatedGradleProject>> {

    @Override
    public List<IsolatedGradleProject> execute(BuildController controller) {
        System.out.println("Running build action to fetch isolated project models");
        GradleBuild buildModel = controller.getBuildModel();
        ArrayList<IsolatedGradleProject> collected = new ArrayList<>();
        for (BasicGradleProject project : buildModel.getProjects()) {
            IsolatedGradleProject projectModel = controller.getModel(project, IsolatedGradleProject.class);
            collected.add(projectModel);
        }

        return collected;
    }

}
