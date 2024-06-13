/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.cc.impl.actions;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaProject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FetchAllIdeaProjects implements BuildAction<FetchAllIdeaProjects.Result> {

    @Override
    public Result execute(BuildController controller) {
        Result result = new Result();

        GradleBuild buildModel = controller.getBuildModel();
        result.rootBuild = buildModel;
        result.rootIdeaProject = controller.getModel(IdeaProject.class);

        collectAllNestedBuilds(buildModel, controller, result);

        result.allIdeaProjects.add(result.rootIdeaProject);
        LinkedHashMap<GradleBuild, IdeaProject> includedWithoutRoot = new LinkedHashMap<>(result.includedBuildIdeaProjects);
        includedWithoutRoot.remove(buildModel);
        result.allIdeaProjects.addAll(includedWithoutRoot.values());

        return result;
    }

    private static void collectAllNestedBuilds(GradleBuild buildModel, BuildController controller, Result result) {
        for (GradleBuild includedBuild : buildModel.getIncludedBuilds()) {
            if (!result.includedBuildIdeaProjects.containsKey(includedBuild)) {
                IdeaProject includedBuildProject = controller.getModel(includedBuild, IdeaProject.class);
                result.includedBuildIdeaProjects.put(includedBuild, includedBuildProject);
                collectAllNestedBuilds(includedBuild, controller, result);
            }
        }
    }

    public static class Result implements Serializable {
        public GradleBuild rootBuild;
        public IdeaProject rootIdeaProject;
        public List<IdeaProject> allIdeaProjects = new ArrayList<>();
        public final Map<GradleBuild, IdeaProject> includedBuildIdeaProjects = new LinkedHashMap<>();

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
