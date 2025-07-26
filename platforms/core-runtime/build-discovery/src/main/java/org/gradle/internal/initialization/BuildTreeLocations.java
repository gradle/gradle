/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.initialization;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Locations required to set up and manage a build tree.
 * <p>
 * The locations are fixed even for a build-session scope, because they can't change during a continuous build.
 */
@ServiceScope(Scope.BuildSession.class)
public class BuildTreeLocations {

    private final BuildLocations rootBuildLocations;

    public BuildTreeLocations(BuildLocations rootBuildLocations) {
        this.rootBuildLocations = rootBuildLocations;
    }

    /**
     * Root directory of the build tree.
     * <p>
     * It always matches the root directory of the root build, which is also the same as the settings directory of the root build.
     * <p>
     * Note that this directory can technically differ from the <code>Project.rootDir</code>, because the latter is mutable during settings
     * via the <code>ProjectDescriptor</code> of the root project.
     */
    public File getBuildTreeRootDirectory() {
        return rootBuildLocations.getBuildRootDirectory();
    }

    /**
     * Locations of the root build.
     */
    public BuildLocations getRoot() {
        return rootBuildLocations;
    }
}
