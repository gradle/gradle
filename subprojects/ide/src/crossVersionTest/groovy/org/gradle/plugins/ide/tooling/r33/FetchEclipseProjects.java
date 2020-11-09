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
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.ArrayList;
import java.util.List;

public class FetchEclipseProjects implements BuildAction<List<EclipseProject>> {

    @Override
    public List<EclipseProject> execute(BuildController controller) {
        List<EclipseProject> eclipseProjects = new ArrayList<>();
        GradleBuild build = controller.getBuildModel();
        ArrayList<GradleBuild> all = new ArrayList<>();
        all.add(build);
        collectEclipseProjects(build, eclipseProjects, controller, all);
        return eclipseProjects;
    }

    private void collectEclipseProjects(GradleBuild build, List<EclipseProject> eclipseProjects, BuildController controller, List<GradleBuild> all) {
        for (BasicGradleProject project : build.getProjects()) {
            eclipseProjects.add(controller.getModel(project, EclipseProject.class));
        }
        for (GradleBuild includedBuild : build.getIncludedBuilds()) {
            if (!all.contains(includedBuild)) {
                all.add(includedBuild);
                collectEclipseProjects(includedBuild, eclipseProjects, controller, all);
            }
        }
    }
}
