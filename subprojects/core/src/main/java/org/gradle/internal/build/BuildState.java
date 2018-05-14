/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.util.Path;

/**
 * Encapsulates the identity and state of a particular build in a build tree.
 */
public interface BuildState {
    BuildIdentifier getBuildIdentifier();

    boolean isImplicitBuild();

    SettingsInternal getLoadedSettings() throws IllegalStateException;

    /**
     * Calculates the identity path for a project in this build.
     */
    Path getIdentityPathForProject(Path projectPath);

    /**
     * Calculates the identifier for a project in this build.
     */
    ProjectComponentIdentifier getIdentifierForProject(Path projectPath);
}
