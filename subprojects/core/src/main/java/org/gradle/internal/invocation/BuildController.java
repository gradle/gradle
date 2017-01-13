/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.invocation;

import org.gradle.api.Nullable;
import org.gradle.api.internal.GradleInternal;

/**
 * This is intended to eventually replace {@link org.gradle.initialization.GradleLauncher} internally. It's pretty rough at the moment.
 */
public interface BuildController {
    /**
     * Adds a listener that receives all events for this build invocation, including those from the current build and all nested builds.
     */
    void addNestedListener(Object listener);

    /**
     * @return The {@link org.gradle.api.internal.GradleInternal} object that represents the build invocation.
     */
    GradleInternal getGradle();

    /**
     * Configures the build and schedules and executes tasks specified in the {@link org.gradle.StartParameter} associated with the build.
     *
     * @return The {@link org.gradle.api.internal.GradleInternal} object that represents the build invocation.
     */
    GradleInternal run();

    /**
     * Configures the build but does not schedule or run any tasks.
     *
     * @return The {@link org.gradle.api.internal.GradleInternal} object that represents the build invocation.
     */
    GradleInternal configure();

    /**
     * Returns true if a result (possibly null) has been specified.
     */
    boolean hasResult();

    /**
     * Returns the result for the build action.
     */
    @Nullable
    Object getResult();

    /**
     * Sets the result for the build action.
     */
    void setResult(@Nullable Object result);
}
