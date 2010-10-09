/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.execution;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.Collections;

public class TaskNameResolver {
    
    public SetMultimap<String, Task> select(String name, Project project) {
        return select(name, (ProjectInternal) project, Collections.<Project>emptySet());
    }

    public SetMultimap<String, Task> selectAll(String name, Project project) {
        return select(name, (ProjectInternal) project, project.getSubprojects());
    }

    private SetMultimap<String, Task> select(String name, ProjectInternal project, Iterable<Project> additionalProjects) {
        SetMultimap<String, Task> selected = LinkedHashMultimap.create();
        Task task = project.getTasks().findByName(name);
        if (task != null) {
            selected.put(task.getName(), task);
        } else {
            task = project.getImplicitTasks().findByName(name);
            if (task != null) {
                selected.put(task.getName(), task);
            }
        }
        for (Project additionalProject : additionalProjects) {
            task = additionalProject.getTasks().findByName(name);
            if (task != null) {
                selected.put(task.getName(), task);
            }
        }
        if (!selected.isEmpty()) {
            return selected;
        }

        for (Task t : project.getTasks()) {
            selected.put(t.getName(), t);
        }
        for (Task t : project.getImplicitTasks()) {
            if (!selected.containsKey(t.getName())) {
                selected.put(t.getName(), t);
            }
        }
        for (Project additionalProject : additionalProjects) {
            for (Task t : additionalProject.getTasks()) {
                selected.put(t.getName(), t);
            }
        }

        return selected;
    }
}
