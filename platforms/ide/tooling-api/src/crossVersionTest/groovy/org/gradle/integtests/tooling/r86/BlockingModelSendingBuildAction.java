/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r86;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.IOException;
import java.net.URI;

class BlockingModelSendingBuildAction implements BuildAction<CustomModel> {
    private final URI uri;

    public BlockingModelSendingBuildAction(URI uri) {
        this.uri = uri;
    }

    @Override
    public CustomModel execute(BuildController controller) {
        System.out.println("ACTION STARTED");
        GradleProject gradleProject = controller.getModel(GradleProject.class);
        controller.send(gradleProject);

        System.out.println("ACTION WAITING");
        try {
            uri.toURL().openConnection().getContentLength();
        } catch (IOException e) {
            e.printStackTrace(System.out);
            throw new RuntimeException(e);
        }

        System.out.println("ACTION DONE");

        EclipseProject eclipseProject = controller.getModel(EclipseProject.class);
        return new CustomModel(eclipseProject.getChildren().size());
    }
}
