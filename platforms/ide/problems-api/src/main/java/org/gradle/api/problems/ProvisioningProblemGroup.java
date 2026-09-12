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
 * The predefined {@code Provisioning} root problem group.
 * <p>
 * Making resources available for use and managing them, including tools and toolchains, services, or infrastructure.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class ProvisioningProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected ProvisioningProblemGroup() {
    }

    /**
     * The {@code Infrastructure} sub-group.
     * <p>
     * Provisioning and lifecycle management of infrastructure resources, including cloud resources, containers, or
     * services.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getInfrastructure();

    /**
     * The {@code Tools and Toolchains} sub-group.
     * <p>
     * Discovery, download, installation, and configuration of tools and toolchains required for the build, including
     * SDKs or arbitrary tools.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getToolsAndToolchains();
}
