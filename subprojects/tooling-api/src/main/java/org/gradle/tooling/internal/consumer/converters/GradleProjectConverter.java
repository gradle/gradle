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

import org.gradle.tooling.internal.gradle.DefaultConvertedGradleProject;
import org.gradle.tooling.internal.gradle.DefaultGradleProjectTask;
import org.gradle.tooling.internal.gradle.DefaultGradleTask;
import org.gradle.tooling.internal.gradle.PartialGradleProject;
import org.gradle.tooling.internal.protocol.TaskVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;

import java.util.LinkedList;
import java.util.List;

public class GradleProjectConverter {

    public DefaultConvertedGradleProject convert(EclipseProjectVersion3 project) {
        //build children recursively
        List<DefaultConvertedGradleProject> children = new LinkedList<DefaultConvertedGradleProject>();
        for (EclipseProjectVersion3 p : project.getChildren()) {
            children.add(convert(p));
        }
        //build parent
        DefaultConvertedGradleProject parent = new DefaultConvertedGradleProject()
                .setPath(project.getPath())
                .setName(project.getName())
                .setChildren(children)
                .setDescription(project.getDescription());

        //build tasks
        List<DefaultGradleTask> tasks = new LinkedList<DefaultGradleTask>();
        for (TaskVersion1 t : project.getTasks()) {
            tasks.add(new DefaultGradleProjectTask()
                    .setProject(parent)
                    .setName(t.getName())
                    .setPath(t.getPath())
                    .setDescription(t.getDescription())
                    );
        }
        parent.setTasks(tasks);

        //apply parent to each child
        for (PartialGradleProject child : children) {
            child.setParent(parent);
        }

        return parent;
    }
}
