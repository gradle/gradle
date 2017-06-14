/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.internal.invocation.BuildController;

public interface NestedBuildFactory {
    /**
     * Creates a nested {@link GradleLauncher} instance with the provided parameters.
     */
    GradleLauncher nestedInstance(StartParameter startParameter);

    /**
     * Creates a {@link BuildController} for nested build instance with the provided parameters.
     * The nested build will be created with a new session.
     */
    BuildController nestedBuildController(StartParameter startParameter);
}
