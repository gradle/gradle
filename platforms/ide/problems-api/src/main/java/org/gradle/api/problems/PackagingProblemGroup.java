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
 * The predefined {@code Packaging} root problem group.
 * <p>
 * Assembling products into various runnable and deployable forms, including creating binaries, archives,
 * installers, or container images, and signing them.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class PackagingProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected PackagingProblemGroup() {
    }

    /**
     * The {@code Distributions} sub-group.
     * <p>
     * Packaging of resources, executables, and launch infrastructure into shippable formats, including archives,
     * container images, distribution packages, or installers.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getDistributions();

    /**
     * The {@code JAR} sub-group.
     * <p>
     * Creation of JARs and manifests.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getJar();

    /**
     * The {@code Native Linking} sub-group.
     * <p>
     * Linking of native binaries, including linker configuration, execution, or symbol resolution.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getNativeLinking();

    /**
     * The {@code Signing} sub-group.
     * <p>
     * Configuration and execution of signing, including certificate management, keystore setup, or signature
     * generation.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getSigning();
}
