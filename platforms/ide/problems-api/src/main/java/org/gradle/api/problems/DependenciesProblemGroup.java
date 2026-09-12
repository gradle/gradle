/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * The predefined {@code Dependencies} root problem group.
 * <p>
 * Declaration, resolution, locking, and verification of dependencies required by projects or the Gradle runtime.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class DependenciesProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected DependenciesProblemGroup() {
    }

    /**
     * The {@code Declaration} sub-group.
     * <p>
     * Dependency declarations, including string notations, rich dependencies, constraints, platforms, or version
     * catalogs; repository declarations, including Maven, Ivy, or content filtering.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getDeclaration();

    /**
     * The {@code Locking} sub-group.
     * <p>
     * Dependency versions locking, including lock files generation, or enforcement of resolved versions.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getLocking();

    /**
     * The {@code Graph Resolution} sub-group.
     * <p>
     * Variant-aware dependency graph resolution, including dependency substitutions, metadata processing, resolution
     * rules, attribute matching, or conflict resolution.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getGraphResolution();

    /**
     * The {@code Artifact Resolution} sub-group.
     * <p>
     * Artifact files resolution from the dependency graph, including compiled libraries, JAR files, AAR files, DLLs,
     * or ZIPs.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getArtifactResolution();

    /**
     * The {@code Verification} sub-group.
     * <p>
     * Dependency integrity and authenticity verification, including verification metadata file, checksum
     * verification, signature checks, or trusted key management.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getVerification();
}
