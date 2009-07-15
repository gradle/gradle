/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.ant;

import org.apache.tools.ant.Target;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskDependency;

import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

public class AntTarget extends ConventionTask {
    private Target target;

    public AntTarget(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                executeAntTarget();
            }
        });

        dependsOn(new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                return getAntTargetDependencies();
            }
        });
    }

    private Set<Task> getAntTargetDependencies() {
        Set<Task> tasks = new LinkedHashSet<Task>();
        Enumeration dependencies = target.getDependencies();
        while (dependencies.hasMoreElements()) {
            String name = (String) dependencies.nextElement();
            tasks.add(getProject().getTasks().getByName(name));
        }
        return tasks;
    }

    private void executeAntTarget() {
        target.performTasks();
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    @Override
    public String getDescription() {
        return target == null ? null : target.getDescription();
    }

    @Override
    public void setDescription(String description) {
        if (target != null) {
            target.setDescription(description);
        }
    }
}
