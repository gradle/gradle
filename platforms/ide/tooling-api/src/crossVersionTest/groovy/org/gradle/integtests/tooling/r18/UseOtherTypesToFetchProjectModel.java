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
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;

import java.util.HashMap;
import java.util.Map;

public class UseOtherTypesToFetchProjectModel implements BuildAction<Map<String, CustomModel>> {
    public Map<String, CustomModel> execute(BuildController controller) {
        // Use an IdeaModule to reference a project
        IdeaProject ideaProject = controller.getModel(IdeaProject.class);
        for (IdeaModule ideaModule : ideaProject.getModules()) {
            visit(ideaModule, controller, new HashMap<String, CustomModel>());
        }

        // Use an EclipseProject to reference a project
        EclipseProject eclipseProject = controller.getModel(EclipseProject.class);
        visit(eclipseProject, controller, new HashMap<String, CustomModel>());

        // Use a GradleProject to reference a project
        GradleProject rootProject = controller.getModel(GradleProject.class);
        Map<String, CustomModel> projects = new HashMap<String, CustomModel>();
        visit(rootProject, controller, projects);
        return projects;
    }

    void visit(HierarchicalElement element, BuildController buildController, Map<String, CustomModel> results) {
        results.put(element.getName(), buildController.getModel(element, CustomModel.class));
        for (HierarchicalElement child : element.getChildren()) {
            visit(child, buildController, results);
        }
    }
}
