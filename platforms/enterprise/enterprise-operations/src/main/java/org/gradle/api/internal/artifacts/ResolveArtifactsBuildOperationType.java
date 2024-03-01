/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.internal.operations.BuildOperationType;

/**
 * Details about an artifact set being resolved.
 *
 * @since 4.0
 */
public final class ResolveArtifactsBuildOperationType implements BuildOperationType<ResolveArtifactsBuildOperationType.Details, ResolveArtifactsBuildOperationType.Result> {

    public interface Details {

        /**
         * This method is not called on the Develocity side, at least in DV plugin &gt;= 3.0.
         *
         * @return An empty string.
         *
         * @deprecated This method will be removed in Gradle 9.0
         */
        @Deprecated
        String getConfigurationPath();

    }

    public interface Result {

    }

    private ResolveArtifactsBuildOperationType() {
    }

}
