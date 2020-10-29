/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.specs.Spec;

public class TaskFilter {

    private final GradleInternal gradle;
    private final Spec<Task> task;

    TaskFilter(GradleInternal gradle, Spec<Task> task) {
        this.gradle = gradle;
        this.task = task;
    }

    GradleInternal getGradle() {
        return gradle;
    }

    Spec<Task> getTask() {
        return task;
    }
}
