/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.locations;

import org.gradle.util.Path;

/**
 * A problem location that stores a task path if the problem was emitted meanwhile executing a task.
 *
 * @since 8.5
 */
public class TaskPathLocation implements ProblemLocation {

    private final Path identityPath;

    public TaskPathLocation(Path identityPath) {
        this.identityPath = identityPath;
    }

    @Override
    public String getType() {
        return "task";
    }

    public Path getIdentityPath() {
        return identityPath;
    }

}
