/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.tooling.r31;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaProject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IdeaProjectUtil {

    public static class GetAllIdeaProjectsAction implements BuildAction<AllProjects> {
        @Override
        public AllProjects execute(BuildController controller) {
            AllProjects result = new AllProjects();

            GradleBuild buildModel = controller.getBuildModel();
            result.rootBuild = buildModel;
            result.rootIdeaProject = controller.getModel(IdeaProject.class);

            for (GradleBuild includedBuild : buildModel.getIncludedBuilds()) {
                IdeaProject includedBuildProject = controller.getModel(includedBuild, IdeaProject.class);
                result.includedBuildIdeaProjects.put(includedBuild, includedBuildProject);
            }

            result.allIdeaProjects.add(result.rootIdeaProject);
            result.allIdeaProjects.addAll(result.includedBuildIdeaProjects.values());

            return result;
        }
    }

    public static class AllProjects implements Serializable {
        public GradleBuild rootBuild;
        public IdeaProject rootIdeaProject;
        public List<IdeaProject> allIdeaProjects = new ArrayList<IdeaProject>();
        public final Map<GradleBuild, IdeaProject> includedBuildIdeaProjects = new LinkedHashMap<GradleBuild, IdeaProject>();

        public IdeaProject getIdeaProject(String name) {
            for (IdeaProject ideaProject : allIdeaProjects) {
                if (ideaProject.getName().equals(name)) {
                    return ideaProject;
                }
            }
            return null;
        }
    }
}
