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

import java.util.Enumeration;

public class AntTask extends ConventionTask {
    private Target target;

    public AntTask(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                executeAntTarget();
            }
        });
    }

    private void executeAntTarget() {
        target.performTasks();
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
        Enumeration dependencies = target.getDependencies();
        while (dependencies.hasMoreElements()) {
            String name = (String) dependencies.nextElement();
            dependsOn(name);
        }
    }
}
