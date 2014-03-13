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
package org.gradle.api.tasks.ant;

import org.apache.tools.ant.Target;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A task which executes an Ant target.
 */
public class AntTarget extends ConventionTask {
    private Target target;
    private File baseDir;

    public AntTarget() {
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
            Task dependency = getProject().getTasks().findByName(name);
            if (dependency == null) {
                throw new UnknownTaskException(String.format("Imported Ant target '%s' depends on target or task '%s' which does not exist", getName(), name));
            }
            tasks.add(dependency);
        }
        return tasks;
    }

    @TaskAction
    protected void executeAntTarget() {
        File oldBaseDir = target.getProject().getBaseDir();
        target.getProject().setBaseDir(baseDir);
        try {
            target.performTasks();
        } finally {
            target.getProject().setBaseDir(oldBaseDir);
        }
    }

    /**
     * Returns the Ant target to execute.
     */
    public Target getTarget() {
        return target;
    }

    /**
     * Sets the Ant target to execute.
     */
    public void setTarget(Target target) {
        this.target = target;
    }

    /**
     * Returns the Ant project base directory to use when executing the target.
     */
    public File getBaseDir() {
        return baseDir;
    }

    /**
     * Sets the Ant project base directory to use when executing the target.
     */
    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return target == null ? null : target.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDescription(String description) {
        if (target != null) {
            target.setDescription(description);
        }
    }
}
