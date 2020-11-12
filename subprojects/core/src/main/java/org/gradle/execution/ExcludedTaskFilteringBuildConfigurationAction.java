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
package org.gradle.execution;

import org.gradle.api.internal.GradleInternal;

/**
 * A {@link BuildConfigurationAction} which filters excluded tasks.
 */
public class ExcludedTaskFilteringBuildConfigurationAction implements BuildConfigurationAction {
    private final TaskFilter taskFilter;

    public ExcludedTaskFilteringBuildConfigurationAction(TaskFilter taskFilter) {
        this.taskFilter = taskFilter;
    }

    @Override
    public void configure(BuildExecutionContext context) {
        GradleInternal gradle = context.getGradle();
        taskFilter.excludeTaskNames(gradle.getStartParameter().getExcludedTaskNames());
        context.proceed();
    }
}
