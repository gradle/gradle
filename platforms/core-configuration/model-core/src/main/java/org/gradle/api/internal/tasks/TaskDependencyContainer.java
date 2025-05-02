/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.tasks;

/**
 * An object that has task dependencies associated with it.
 */
public interface TaskDependencyContainer {
    TaskDependencyContainer EMPTY = context -> {
    };

    /**
     * Adds the dependencies from this container to the given context. Failures to calculate the build dependencies should be supplied to the context rather than thrown.
     */
    void visitDependencies(TaskDependencyResolveContext context);
}
