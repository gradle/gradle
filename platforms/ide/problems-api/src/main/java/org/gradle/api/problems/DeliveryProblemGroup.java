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
 * The predefined {@code Delivery} root problem group.
 * <p>
 * Making products available to users, including publishing software components, deploying applications, or
 * uploading documentation.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class DeliveryProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected DeliveryProblemGroup() {
    }

    /**
     * The {@code Publishing} sub-group.
     * <p>
     * Making products available for others to fetch, such as Maven or Ivy artifacts in repositories, libraries in
     * package registries, container images in registries, applications in stores, or documentation and distributions
     * on websites.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getPublishing();

    /**
     * The {@code Deployment} sub-group.
     * <p>
     * Making products ready for others to use, such as deploying containers and Kubernetes manifests, application
     * archives like WARs or EARs on application servers, serverless functions, or static sites.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getDeployment();
}
