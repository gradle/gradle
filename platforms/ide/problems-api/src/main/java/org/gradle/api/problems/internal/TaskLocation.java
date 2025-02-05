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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.ProblemLocation;

/**
 * Task location.
 */
public interface TaskLocation extends ProblemLocation {

    /**
     * The absolute build tree path of the task reporting the problem.
     */
    String getBuildTreePath();

    /**
     * The build path the tasks belongs to.
     */
    String getBuildPath();

    /**
     * The task path.
     * <p>
     * This is the task path in the build denoted by {@link #getBuildPath()}.
     */
    String getTaskPath();
}
