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

package org.gradle;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * A request to execute some tasks, along with an optional project path context to provide information necessary to select the tasks
 *
 * @since 2.0
 */
public interface TaskExecutionRequest {
    /**
     * The arguments to use to select and optionally configure the tasks, as if provided on the command-line.
     *
     * @return task name.
     */
    List<String> getArgs();

    /**
     * Project path associated with this task request if any.
     *
     * @return project path or {@code null} to use the default project path.
     */
    @Nullable String getProjectPath();

    /**
     * The root folder of the build that this task was defined in.
     *
     * @return the root project folder or {@code null} if the information is not available.
     * @since 3.3
     */
    @Nullable
    File getRootDir();
}
