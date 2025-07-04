/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild

class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {

    @Override
    public MyCustomModel execute(BuildController controller) {
        GradleBuild build = controller.getModel(GradleBuild.class);

//            IdeaProject ideaProject = controller.getModel(IdeaProject.class);

        def paths = build.projects.collect{project ->
            project.buildTreePath
        }
        build.includedBuilds.each {b -> b.projects.each {project ->
            paths << project.buildTreePath
        }}

        def identifier = build.projects.collect{project ->
            project.projectIdentifier
        }
        // Build your custom model
        return new MyCustomModel(
            null,
            identifier,
            paths
//                rootProject.getName(),
//                rootProject.getProjectDirectory(),
//                ideaProject.getModules()
        );
    }
}
