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
package org.gradle.tooling.events.task;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.model.UnsupportedMethodException;

import java.util.Set;

/**
 * Describes a task operation for which an event has occurred.
 *
 * @since 2.5
 */
public interface TaskOperationDescriptor extends OperationDescriptor {

    /**
     * Returns the path of the task.
     */
    String getTaskPath();

    /**
     * Returns the dependencies of the task, if available.
     *
     * @return The dependencies of the task
     * @throws UnsupportedMethodException For Gradle versions older than 5.1, where this method is not supported.
     * @since 5.1
     */
    @Incubating
    Set<? extends OperationDescriptor> getDependencies() throws UnsupportedMethodException;

}
