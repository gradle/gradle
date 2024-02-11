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

package org.gradle.plugins.ide.tooling.r72;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

public class FetchBuildSrcModelAction implements BuildAction<GradleProject> {
    @Override
    public GradleProject execute(BuildController controller) {
        GradleBuild buildModel = controller.getModel(GradleBuild.class);
        for (GradleBuild build : buildModel.getEditableBuilds()) {
            if (build.getBuildIdentifier().getRootDir().getName().equals("buildSrc")) {
                return controller.getModel(build, GradleProject.class);
            }
        }
        return null;
    }
}
