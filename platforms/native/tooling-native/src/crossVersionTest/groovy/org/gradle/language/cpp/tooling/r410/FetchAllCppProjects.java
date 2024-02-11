/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.cpp.tooling.r410;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.cpp.CppProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FetchAllCppProjects implements BuildAction<List<CppProject>>, Serializable {
    @Override
    public List<CppProject> execute(BuildController controller) {
        List<CppProject> projects = new ArrayList<CppProject>();
        collectModelsForBuild(controller, controller.getBuildModel(), projects);
        for (GradleBuild build : controller.getBuildModel().getEditableBuilds()) {
            collectModelsForBuild(controller, build, projects);
        }
        return projects;
    }

    private void collectModelsForBuild(BuildController controller, GradleBuild build, List<CppProject> projects) {
        for (BasicGradleProject project : build.getProjects()) {
            projects.add(controller.getModel(project, CppProject.class));
        }
    }
}
