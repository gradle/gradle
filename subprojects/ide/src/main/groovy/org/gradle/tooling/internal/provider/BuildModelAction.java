/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider;

import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.tooling.internal.protocol.ProjectVersion3;

import java.util.List;

import static java.util.Arrays.asList;

public class BuildModelAction implements GradleLauncherAction<ProjectVersion3> {
    private ModelBuildingAdapter modelBuildingAdapter;

    public BuildModelAction(Class<? extends ProjectVersion3> type) {
        List<? extends BuildsModel> modelBuilders = asList(
                new EclipseModelBuilder(), new IdeaModelBuilder(), new GradleProjectBuilder(), new BasicIdeaModelBuilder());

        for (BuildsModel builder : modelBuilders) {
            if (builder.canBuild(type)) {
                modelBuildingAdapter = new ModelBuildingAdapter(builder);
                return;
            }
        }

        throw new UnsupportedOperationException(String.format("Do not know how to build a model of type '%s'.", type.getSimpleName()));
    }

    public BuildResult run(GradleLauncher launcher) {
        launcher.addListener(modelBuildingAdapter);
        return launcher.getBuildAnalysis();
    }

    public ProjectVersion3 getResult() {
        return modelBuildingAdapter.getProject();
    }
}
