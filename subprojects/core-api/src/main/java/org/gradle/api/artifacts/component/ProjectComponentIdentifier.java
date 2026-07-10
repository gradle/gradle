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

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * An identifier for a component instance that is built as part of the current build.
 *
 * @since 1.10
 */
@UsedByScanPlugin
@HasInternalProtocol
public interface ProjectComponentIdentifier extends ComponentIdentifier {
    /**
     * Identifies the build that contains the project that produces this component.
     *
     * @return The build identifier
     */
    BuildIdentifier getBuild();

    /**
     * Returns the path of the project that produces this component. This path is relative to the containing build, so for example will return ':' for the root project of a build.
     *
     * @since 1.10
     */
    String getProjectPath();

    /**
     * Returns a path to the project for the full build tree.
     *
     * @return The build tree path
     * @since 8.3
     */
    @Incubating
    String getBuildTreePath();

    /**
     * Returns the simple name of the project that produces this component.
     *
     * @since 4.5
     */
    String getProjectName();
}
