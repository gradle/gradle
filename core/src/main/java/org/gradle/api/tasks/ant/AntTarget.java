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
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

/**
 * A task which executes an Ant target.
 */
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
public class AntTarget extends ConventionTask {

    private Target target;
    private File baseDir;

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
    @Internal
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
    @Internal
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
    @Internal
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
