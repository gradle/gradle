/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.initialization.TaskSchedulingPreparer;

public class DefaultTaskSchedulingPreparer implements TaskSchedulingPreparer {
    private final TaskSchedulingPreparer delegate;

    public DefaultTaskSchedulingPreparer(TaskSchedulingPreparer delegate) {
        this.delegate = delegate;
    }

    @Override
    public void prepareForTaskScheduling(GradleInternal gradle, ExecutionPlan executionPlan) {
        delegate.prepareForTaskScheduling(gradle, executionPlan);
    }
}
