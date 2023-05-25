/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationServices;
import org.gradle.api.ProjectExecutionServices;
import org.gradle.api.Task;
import org.gradle.api.TaskAllTimeServices;
import org.gradle.api.TaskExecutionServices;
import org.gradle.api.internal.project.ProjectInternal;

@SuppressWarnings("unused")
public class ServicesExtensionModule {

    public static ProjectConfigurationServices getDslServices(Project project) {
        return ((ProjectInternal) project).getServices().get(ProjectConfigurationServices.class);
    }

    public static ProjectExecutionServices getExecutionServices(Project project) {
        return ((ProjectInternal) project).getServices().get(ProjectExecutionServices.class);
    }

    public static TaskAllTimeServices getDslServices(Task task) {
        return ((DefaultTask) task).getServices().get(TaskAllTimeServices.class);
    }

    public static TaskExecutionServices getExecutionServices(Task task) {
        return ((DefaultTask) task).getServices().get(TaskExecutionServices.class);
    }
}
