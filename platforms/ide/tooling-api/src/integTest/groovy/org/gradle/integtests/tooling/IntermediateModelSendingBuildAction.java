/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.tooling;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.eclipse.EclipseProject;

class IntermediateModelSendingBuildAction implements BuildAction<CustomModel> {
    public CustomModel execute(BuildController controller) {
        EclipseProject eclipseProject = controller.getModel(EclipseProject.class);
        GradleProject gradleProject = controller.getModel(GradleProject.class);

        // Intentionally sending models in an order different from requesting models
        controller.sendIntermediate(gradleProject);
        controller.sendIntermediate(eclipseProject);

        return new CustomModel(42);
    }
}
