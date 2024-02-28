/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildconfiguration.tasks;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.project.ProjectConfigureAction;
import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults;

public class UpdateDaemonJvmTaskConfigurator implements ProjectConfigureAction {

    public static final String TASK_NAME = "updateDaemonJvm";

    @Override
    public void execute(ProjectInternal project) {
        project.getTasks().register(TASK_NAME, UpdateDaemonJvmTask.class, task -> {
            task.setGroup("Build Setup");
            task.setDescription("Generates or updates the Daemon JVM criteria.");
            task.getToolchainVersion().convention(BuildPropertiesDefaults.TOOLCHAIN_VERSION);
        });
    }
}
