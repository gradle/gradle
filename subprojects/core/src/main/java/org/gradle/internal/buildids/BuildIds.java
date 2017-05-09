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

package org.gradle.internal.buildids;

import org.gradle.internal.id.UniqueId;

/**
 * IDs for the current build.
 *
 * @since 4.0
 */
public class BuildIds {

    private final UniqueId buildId;

    public BuildIds(UniqueId buildId) {
        this.buildId = buildId;
    }

    /**
     * An ID for the current build invocation/execution.
     *
     * All nested builds in the same invocation share the same ID.
     * Each execution that is part of a continuous build is assigned a new ID.
     */
    public UniqueId getBuildId() {
        return buildId;
    }

}
