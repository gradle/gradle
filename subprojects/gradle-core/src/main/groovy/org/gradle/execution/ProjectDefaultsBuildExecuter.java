/*
 * Copyright 2008 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.ImplicitTasksConfigurer;
import org.gradle.util.GUtil;

import java.util.Arrays;
import java.util.List;

/**
 * A {@link BuildExecuter} which selects the default tasks for a project.
 */
public class ProjectDefaultsBuildExecuter extends DelegatingBuildExecuter {
    private List<String> defaultTasks;
    private String displayName;

    public void select(GradleInternal gradle) {
        if (getDelegate() == null) {
            // Gather the default tasks from this first group project
            ProjectInternal project = gradle.getDefaultProject();
            defaultTasks = project.getDefaultTasks();
            displayName = String.format("project default tasks %s", GUtil.toString(defaultTasks));
            if (defaultTasks.size() == 0) {
                defaultTasks = Arrays.asList(ImplicitTasksConfigurer.HELP_TASK);
                displayName = String.format("default task %s", GUtil.toString(defaultTasks));
            }
            setDelegate(new TaskNameResolvingBuildExecuter(defaultTasks));
        }

        super.select(gradle);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}
