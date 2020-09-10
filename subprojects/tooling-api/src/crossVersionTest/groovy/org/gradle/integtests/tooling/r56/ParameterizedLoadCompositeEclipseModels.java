/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.integtests.tooling.r56;

import org.gradle.api.Action;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.eclipse.EclipseRuntime;
import org.gradle.tooling.model.eclipse.EclipseWorkspace;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class ParameterizedLoadCompositeEclipseModels<T> implements BuildAction<Collection<T>>, Serializable {

    private final EclipseWorkspace workspace;
    private final Class<T> modelClass;

    ParameterizedLoadCompositeEclipseModels(EclipseWorkspace workspace, Class<T> modelClass) {
        this.workspace = workspace;
        this.modelClass = modelClass;
    }

    @Override
    public Collection<T> execute(BuildController controller) {
        Collection<T> models = new ArrayList<T>();
        collectRootModels(controller, controller.getBuildModel(), models);
        return models;
    }

    private void collectRootModels(BuildController controller, GradleBuild build, Collection<T> models) {
        if (workspace != null) {
            models.add(controller.getModel(build.getRootProject(), modelClass, EclipseRuntime.class, new EclipseRuntimeAction(workspace)));
        } else {
            models.add(controller.getModel(build.getRootProject(), modelClass));
        }
        for (GradleBuild includedBuild : build.getIncludedBuilds()) {
            collectRootModels(controller, includedBuild, models);
        }
    }

    private static class EclipseRuntimeAction implements Action<EclipseRuntime>, Serializable {
        private final EclipseWorkspace workspace;

        public EclipseRuntimeAction(EclipseWorkspace workspace) {

            this.workspace = workspace;
        }

        @Override
        public void execute(EclipseRuntime eclipseRuntime) {
            eclipseRuntime.setWorkspace(workspace);
        }
    }
}
