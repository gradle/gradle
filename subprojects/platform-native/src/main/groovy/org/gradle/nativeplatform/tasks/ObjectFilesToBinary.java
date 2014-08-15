/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.Task;

/**
 * A task that combines a set of object files into a single binary.
 */
@Incubating
public interface ObjectFilesToBinary extends Task {
    /**
     * Adds a set of object files to be combined into the file binary.
     * The provided source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    void source(Object source);
}
