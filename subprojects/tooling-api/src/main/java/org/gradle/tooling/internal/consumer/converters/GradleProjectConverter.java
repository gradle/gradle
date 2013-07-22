/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.internal.gradle.DefaultGradleTask;
import org.gradle.tooling.internal.protocol.TaskVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.model.GradleTask;

import java.util.LinkedList;
import java.util.List;

public class GradleProjectConverter {

    public DefaultGradleProject convert(EclipseProjectVersion3 project) {
        //build children recursively
        List<DefaultGradleProject> children = new LinkedList<DefaultGradleProject>();
        for (EclipseProjectVersion3 p : project.getChildren()) {
            children.add(convert(p));
        }
        //build parent
        DefaultGradleProject parent = new DefaultGradleProject()
                .setPath(project.getPath())
                .setName(project.getName())
                .setChildren(children)
                .setDescription(project.getDescription());

        //build tasks
        List<GradleTask> tasks = new LinkedList<GradleTask>();
        for (TaskVersion1 t : project.getTasks()) {
            tasks.add(new DefaultGradleTask()
                    .setName(t.getName())
                    .setPath(t.getPath())
                    .setDescription(t.getDescription())
                    .setProject(parent));
        }
        parent.setTasks(tasks);

        //apply parent to each child
        for (DefaultGradleProject child : children) {
            child.setParent(parent);
        }

        return parent;
    }
}
