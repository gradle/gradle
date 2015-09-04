/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.progress;

import org.gradle.api.Nullable;

/**
 * Meta-data about a build operation.
 */
public class BuildOperationDetails {
    private final String displayName;
    private final String progressDisplayName;

    private BuildOperationDetails(String displayName, String progressDisplayName) {
        this.displayName = displayName;
        this.progressDisplayName = progressDisplayName;
    }

    /**
     * Returns the display name for the operation. This should be a standalone human consumable description of the
     * operation, and should describe the operation whether currently running or not, eg "run test A" rather than
     * "running test A".
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the display name to use for progress logging for the operation. Should be short and describe the operation
     * as it is running, eg "running test A" rather than "run test A".
     *
     * <p>When null, no progress logging is generated for the operation. Defaults to null.
     */
    @Nullable
    public String getProgressDisplayName() {
        return progressDisplayName;
    }

    public static Builder displayName(String displayName) {
        return new Builder(displayName);
    }

    public static class Builder {
        private final String displayName;
        private String progressDisplayName;

        private Builder(String displayName) {
            this.displayName = displayName;
        }

        public Builder progressDisplayName(String progressDisplayName) {
            this.progressDisplayName = progressDisplayName;
            return this;
        }

        public BuildOperationDetails build() {
            return new BuildOperationDetails(displayName, progressDisplayName);
        }
    }
}
