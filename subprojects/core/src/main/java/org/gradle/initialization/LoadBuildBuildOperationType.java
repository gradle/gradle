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

package org.gradle.initialization;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

public final class LoadBuildBuildOperationType implements BuildOperationType<LoadBuildBuildOperationType.Details, LoadBuildBuildOperationType.Result> {
    @UsedByScanPlugin
    public interface Details {
        /**
         * The path of the build configuration that contains these projects.
         * This will be ':' for top-level builds. Nested builds will have a sub-path.
         *
         * @since 4.6
         * @see org.gradle.api.internal.GradleInternal#getIdentityPath()
         */
        String getBuildPath();
    }

    public interface Result {
    }
}
