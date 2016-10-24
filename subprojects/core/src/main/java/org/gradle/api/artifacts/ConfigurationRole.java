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
    CAN_BE_QUERIED_OR_CONSUMED("can be used when building or publishing", true, true),
    CAN_BE_QUERIED_ONLY("It is possible to query this configuration or resolve it", true, false),
    CAN_BE_CONSUMED_ONLY("can be used only when consuming or publishing the project", false, true),
    BUCKET("Used as a bucket of dependencies, not supposed to be consumed or built directly", false, false);

    private final String description;
    private final boolean canBeQueriedOrResolved;
    private final boolean canBeConsumedOrPublished;

    ConfigurationRole(String desc, boolean canBeQueriedOrResolved, boolean canBeConsumedOrPublished) {
        this.description = desc;
        this.canBeQueriedOrResolved = canBeQueriedOrResolved;
        this.canBeConsumedOrPublished = canBeConsumedOrPublished;
    }

    public String getDescription() {
        return description;
    }

    public boolean canBeQueriedOrResolved() {
        return canBeQueriedOrResolved;
    }

    public boolean canBeConsumedOrPublished() {
        return canBeConsumedOrPublished;
    }
}
