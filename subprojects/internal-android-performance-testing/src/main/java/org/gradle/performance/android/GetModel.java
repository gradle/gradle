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
package org.gradle.performance.android;

import com.android.builder.model.AndroidProject;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.Map;
import java.util.TreeMap;

public class GetModel implements BuildAction<Map<String, AndroidProject>> {
    @Override
    public Map<String, AndroidProject> execute(BuildController controller) {
        System.out.println("* Building models");
        Timer timer = new Timer();
        GradleBuild build = controller.getBuildModel();
        Map<String, AndroidProject> result = new TreeMap<String, AndroidProject>();
        for (BasicGradleProject project : build.getProjects()) {
            AndroidProject androidProject = controller.findModel(project, AndroidProject.class);
            result.put(project.getPath(), androidProject);
        }
        timer.stop();
        System.out.println("building models took " + timer.duration());

//            new Inspector().inspectModel(result);
        return result;
    }
}
