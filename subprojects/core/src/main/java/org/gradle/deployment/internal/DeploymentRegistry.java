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

package org.gradle.deployment.internal;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * A registry of deployment handles.
 *
 * @since 4.2
 */
@Incubating
@ThreadSafe
public interface DeploymentRegistry {
    /**
     * Creates and starts a given deployment handle in the registry.
     *
     * @param name name of deployment
     * @param changeBehavior how the deployment responds to potential changes
     * @param handleType type of deployment handle
     * @param params constructor arguments
     *
     * @throws IllegalStateException if deployment handle with the given name already exists
     */
    <T extends DeploymentHandle> T start(String name, ChangeBehavior changeBehavior, Class<T> handleType, Object... params);

    /**
     * Retrieves a deployment handle from the registry with the given name and type.
     *
     * @return the registered deployment handle; null if no deployment is registered with the given name
     */
    @Nullable
    <T extends DeploymentHandle> T get(String name, Class<T> handleType);

    /**
     * Behavior when a deployment is out-of-date.
     */
    @Incubating
    enum ChangeBehavior {
        /**
         * When changes are detected, wait for a deployment request before rebuilding.
         *
         * The deployment needs to call {@link Deployment#status()} to trigger a rebuild wait for changes.
         */
        BLOCK_AND_REBUILD,

        /**
         * When changes are detected, block the deployment until all changes are incorporated.
         *
         * The deployment needs to call {@link Deployment#status()} to wait for changes.
         */
        BLOCK,

        /**
         * When changes are incorporated, automatically stop and start the deployment.
         */
        RESTART,

        /**
         * Do nothing.
         */
        NONE
    }
}
