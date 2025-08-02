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
        GradleBuild build
        try {

         build = controller.getModel(GradleBuild.class);
        }
        catch (Exception e) {
            System.err.println(e.toString());
        }

        if(build.didItFail()){
            System.err.println("Build failed: " + build.failure);
        }


        if(build.includedBuilds.size() > 0) {
            GradleBuild b = build.includedBuilds.getAt(0)
            if(b.didItFail()){
                System.err.println("Build failed: " + b.failure.description);
            }
        }
//        KotlinDslScriptsModel buildScriptModel = controller.getModel(KotlinDslScriptsModel.class);


        def paths = build.projects.collect{project ->
            project.buildTreePath
        }
        build.includedBuilds.each {gb -> gb.projects.each {project ->
            paths << project.buildTreePath
        }}

        def identifier = build.projects.collect{project ->
            project.projectIdentifier
        }

        // Build your custom model
        return new MyCustomModel(
            [:],
            identifier,
            paths,
            build
        );
    }
}
