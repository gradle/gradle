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

import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/**
 * A configurable version constraint. This is exposed to the build author, so that one can express
 * more constraints on a module version.
 *
 * @since 4.4
 */
@HasInternalProtocol
public interface MutableVersionConstraint extends VersionConstraint {
    /**
     * Returns the branch to select versions from. When not {@code null}, select only versions that were built from the given branch.
     *
     * @since 4.6
     */
    @Override
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
     * Sets the version as strict.
     * <p>
     * Any version not matched by this version notation will be excluded. This is the strongest version declaration.
     * It will cause dependency resolution to fail if no version acceptable by this clause can be selected.
     * This term supports dynamic versions.
     * <p>
     * This will override a previous {@link #require(String) require} declaration.
     * <p>
     * This clears any set rejected versions.
     *
     * @param version the strict version to be used for this module
     */
    void strictly(String version);

    /**
     * Sets the required version of this module.
     * <p>
     * Implies that the selected version cannot be lower than what {@code require} accepts but could be higher through conflict resolution, even if higher has an exclusive higher bound.
     * This is what a direct dependency translates to.
     * This term supports dynamic versions.
     * <p>
     * This will override a previous {@link #strictly(String) strictly} declaration.
     * <p>
     * This clears any set rejected versions.
     *
     * @param version the required version of this module
     * @since 5.0
     */
    void require(String version);

    /**
     * Sets the preferred version of this module.
     * <p>
     * This is a very soft version declaration.
     * It applies only if there is no stronger non dynamic opinion on a version for the module.
     * This term does not support dynamic versions.
     * <p>
     * This can complement a {@link #strictly(String) strictly} or {@link #require(String) require} indication.
     * <p>
     * This clears any set rejected versions.
     *
     * @param version the preferred version of this module
     */
    void prefer(String version);

    /**
     * Declares a list of rejected versions. If such a version is found during dependency resolution, it will not
     * be selected. This term supports dynamic versions.
     *
     * @param versions the rejected versions
     *
     * @since 4.5
     */
    void reject(String... versions);

    /**
     * Rejects all versions of this component. Can be used to make sure that if such a component is seen in a
     * dependency graph, resolution fails.
     *
     * @since 4.5
     */
    void rejectAll();

}
