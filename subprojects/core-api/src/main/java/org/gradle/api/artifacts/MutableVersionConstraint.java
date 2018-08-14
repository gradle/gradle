/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/**
 * A configurable version constraint. This is exposed to the build author, so that one can express
 * more constraints on a version,
 *
 * @since 4.4
 */
@Incubating
@HasInternalProtocol
public interface MutableVersionConstraint extends VersionConstraint {
    /**
     * Returns the branch to select versions from. When not {@code null}, select only versions that were built from the given branch.
     *
     * @since 4.6
     */
    @Nullable
    String getBranch();

    /**
     * Specifies the branch to select versions from.
     *
     * @param branch The branch, possibly null.
     * @since 4.6
     */
    void setBranch(@Nullable String branch);

    /**
     * Sets the required version of this module. Any other version constraints will be overriden.
     * @param version the preferred version of this module
     * @since 4.11
     */
    void require(String version);

    /**
     * Sets the preferred version of this module. Any other version constraints will be overriden.
     * @param version the preferred version of this module
     */
    void prefer(String version);

    /**
     * Sets the version as strict, meaning that if any other dependency version for this module disagrees with
     * this version, resolution will fail. Any other version constraints will be overriden.
     *
     * @param version the strict version to be used for this module
     */
    void strictly(String version);

    /**
     * Declares a list of rejected versions. If such a version is found during dependency resolution, it will not
     * be selected.
     *
     * @param versions the rejected versions
     *
     * @since 4.5
     */
    void reject(String... versions);

    /**
     * Rejects all versions of this component. Can be used to declare that a component is incompatible with another
     * (typically, cannot have both a 2 different implementations of the same API).
     *
     * @since 4.5
     */
    void rejectAll();

}
