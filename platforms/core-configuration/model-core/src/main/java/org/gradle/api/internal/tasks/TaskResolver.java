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
package org.gradle.api.internal.tasks;

import org.gradle.api.Task;
import org.gradle.util.Path;

/**
 * Resolves a {@link Path} to a task to the task itself.
 * <p>
 * Note: This resolver is not IP-safe, as it finds and exposes mutable task
 * instances from other mutable project instances. Avoid this resolver if possible.
 */
public interface TaskResolver {

    /**
     * Get the task with the given path.
     * <p>
     * Implementations may choose whether relative paths are permitted,
     * and how those relative paths are resolved to absolute task paths.
     *
     * @throws RuntimeException If the given path is not a valid task path, the requested
     * project does not exist, the requested task does not exist within the requested project,
     * or the given path is not absolute and the resolver does not support resolving relative
     * task paths.
     */
    Task resolveTask(Path path);

}
