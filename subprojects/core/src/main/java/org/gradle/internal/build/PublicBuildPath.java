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

import org.gradle.api.internal.GradleInternal;
import org.gradle.util.Path;

/**
 * A reference to the, eventually available, public path for a build.
 *
 * The state complexity of this object is due to https://github.com/gradle/gradle/issues/4241.
 * The user facing “identity path” of domain objects (e.g. tasks, configurations) is based on this value.
 * This ends up being derived from {@link GradleInternal#getIdentityPath()}.
 * As such, {@link GradleInternal} is injected into a lot of places just to get this path.
 * Moreover, this is required to be wired in to some places that do not have access to the {@link GradleInternal} object.
 *
 * Usages of {@link GradleInternal#getIdentityPath()} should be migrated to this type, to avoid unnecessary penetration of GradleInternal.
 */
public interface PublicBuildPath {

    /**
     * The build's, unique, build path.
     *
     * Throws IllegalStateException if the path is not yet known for the build.
     * This can happen for nested builds as the path is influenced by the root project name.
     */
    Path getBuildPath() throws IllegalStateException;

}
