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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.platform.base.BinaryTasksCollection;

/**
 * Provides access to key tasks used for building the binary.
 */
@Incubating
public interface NativeBinaryTasks extends BinaryTasksCollection {
    /**
     * The link task, if one is present. Null otherwise.
     */
    Task getLink();

    /**
     * The create static library task if present. Null otherwise.
     */
    Task getCreateStaticLib();

    /**
     * The task that builds the binary out of object files: either the link task or create static library task as appropriate.
     */
    ObjectFilesToBinary getCreateOrLink();
}
