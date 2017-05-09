/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.jacoco;

import com.google.common.collect.Sets;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Cast;
import org.gradle.process.JavaForkOptions;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;

import java.util.Set;

/**
 * Internal task to lazily configure Java agent JVM args
 */
public class JacocoConfigure extends DefaultTask {
    private final Set<Task> configurables = Sets.newHashSet();

    /**
     * Adds a task that needs to be finalized just before execution of the task.
     */
    public <T extends Task & JavaForkOptions> void configureTask(T task) {
        configurables.add(task);
        task.dependsOn(this);
    }

    @TaskAction
    public void configureTasks() {
        for (Task task : configurables) {
            JacocoTaskExtension extension = task.getExtensions().getByType(JacocoTaskExtension.class);
            if (extension.isEnabled()) {
                Cast.cast(JavaForkOptions.class, task).jvmArgs(extension.getAsJvmArg());
            }
        }
    }
}
