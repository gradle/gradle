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
package org.gradle.api.artifacts.component;

import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Criteria for selecting a component instance that is built as part of the current build.
 *
 * @since 1.10
 */
@UsedByScanPlugin
@HasInternalProtocol
public interface ProjectComponentSelector extends ComponentSelector {
    /**
     * Absolute build path of the build within the Gradle invocation to select a project from.
     *
     * @since 8.2
     */
    String getBuildPath();

    /**
     * The name of the build to select a project from.
     *
     * @return The build name
     * @deprecated Use {@link #getBuildPath()} instead.
     */
    @Deprecated
    String getBuildName();

    /**
     * The path of the project to select the component from.
     *
     * @return Project path
     * @since 1.10
     */
    String getProjectPath();
}
