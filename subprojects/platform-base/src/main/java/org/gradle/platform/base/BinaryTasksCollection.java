/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.model.internal.core.UnmanagedStruct;

/**
 * A collection of tasks associated to a binary
 */
@Incubating
@UnmanagedStruct
public interface BinaryTasksCollection extends DomainObjectSet<Task> {
    /**
     * Generates a name for a task that performs some action on the binary.
     */
    String taskName(String verb);

    /**
     * Generates a name for a task that performs some action on the binary.
     */
    String taskName(String verb, String object);

    /**
     * The task that can be used to assemble this binary.
     */
    Task getBuild();

    /**
     * The task that can be used to check this binary.
     */
    Task getCheck();

    <T extends Task> void create(String name, Class<T> type, Action<? super T> config);

}
