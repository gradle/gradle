/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.runtime.base.ProjectComponent;
import org.gradle.runtime.base.ProjectComponentContainer;

/**
 * Displays some details about the software components produced by the project.
 */
@Incubating
public class ComponentReport extends DefaultTask {
    @TaskAction
    public void report() {
        Project project = getProject();
        System.out.println("This is the component report for " + project);

        ProjectComponentContainer components = project.getExtensions().findByType(ProjectComponentContainer.class);
        if (components == null || components.isEmpty()) {
            System.out.println("No components.");
            return;
        }
        for (ProjectComponent component : components) {
            System.out.println(component);
        }
    }
}
