/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Incubating;

/**
 * A configuration role defines the role of a configuration during dependency resolution.
 */
@Incubating
public enum ConfigurationRole {
    /**
     * Use this if a configuration can be used either when building or publishing.
     */
    CAN_BE_QUERIED_OR_CONSUMED(true, true),

    /**
     * Use this if it's possible to query this configuration or resolve it only.
     */
    CAN_BE_QUERIED_ONLY(true, false),

    /**
     * Use this if the configuration can be used only when consuming or publishing the project.
     */
    CAN_BE_CONSUMED_ONLY(false, true),

    /**
     * Use this if the configuration is sed as a bucket of dependencies, not supposed to be consumed or built directly.
     * This typically includes the case where you define a parent configuration where the user would declare its dependencies
     * but only child configurations are supposed to be used for resolution.
     */
    BUCKET(false, false);

    private final boolean canBeQueriedOrResolved;
    private final boolean canBeConsumedOrPublished;

    ConfigurationRole(boolean canBeQueriedOrResolved, boolean canBeConsumedOrPublished) {
        this.canBeQueriedOrResolved = canBeQueriedOrResolved;
        this.canBeConsumedOrPublished = canBeConsumedOrPublished;
    }

    public boolean canBeQueriedOrResolved() {
        return canBeQueriedOrResolved;
    }

    public boolean canBeConsumedOrPublished() {
        return canBeConsumedOrPublished;
    }
}
