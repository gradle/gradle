/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r33;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.ArrayList;
import java.util.List;

public class FetchBuildEnvironments implements BuildAction<List<BuildEnvironment>> {
    @Override
    public List<BuildEnvironment> execute(BuildController controller) {
        List<BuildEnvironment> environments = new ArrayList<BuildEnvironment>();
        GradleBuild build = controller.getBuildModel();
        environments.add(controller.getModel(build, BuildEnvironment.class));
        for (GradleBuild includedBuild : build.getIncludedBuilds()) {
            environments.add(controller.getModel(includedBuild, BuildEnvironment.class));
        }
        return environments;
    }
}
