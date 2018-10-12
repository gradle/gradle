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

import org.gradle.internal.HasInternalProtocol;

/**
 * A deployed application.
 *
 * @since 4.2
 */
@HasInternalProtocol
public interface Deployment {
    /**
     * Returns the latest status for this deployment.
     *
     * <p>
     * This method may block until all pending changes have been incorporated.
     * </p>
     * @return the current status of this deployment.
     */
    Status status();

    /**
     * Status of a Deployment
     */
    interface Status {
        /**
         * Returns a Throwable if the latest build failed for this deployment.
         * @return any failure for the current status.
         */
        Throwable getFailure();

        /**
         * Returns true if the deployment's runtime may have changed since the previous status was reported.
         * @return whether the deployment runtime may have changed.
         */
        boolean hasChanged();
    }
}
