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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

/**
 * A task which executes an Ant target.
 */
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
public abstract class AntTarget extends ConventionTask {

    @TaskAction
    protected void executeAntTarget() {
        Target target = getTarget().get();
        File oldBaseDir = target.getProject().getBaseDir();
        try {
            target.getProject().setBaseDir(getBaseDir().getAsFile().get());
            target.performTasks();
        } finally {
            target.getProject().setBaseDir(oldBaseDir);
        }
    }

    /**
     * Returns the Ant target to execute.
     */
    @Internal
    @ReplacesEagerProperty
    public abstract Property<Target> getTarget();

    /**
     * Returns the Ant project base directory to use when executing the target.
     */
    @Internal
    @ReplacesEagerProperty
    public abstract DirectoryProperty getBaseDir();

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ToBeReplacedByLazyProperty
    public String getDescription() {
        return getTarget().map(Target::getDescription).getOrNull();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDescription(String description) {
        Target target = getTarget().getOrNull();
        if (target != null) {
            target.setDescription(description);
        }
    }
}
