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

package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;

/**
 * This is intended to eventually replace {@link GradleLauncher} internally. It's pretty rough at the moment.
 */
public interface BuildController {
    /**
     * Specifies the start parameter to use to run the build. This method cannot be called after any other methods have been called on this controller.
     */
    void setStartParameter(StartParameter startParameter);

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
}
