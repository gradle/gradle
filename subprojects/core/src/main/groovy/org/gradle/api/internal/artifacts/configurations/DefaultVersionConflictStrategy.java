/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.VersionConflictStrategy;
import org.gradle.api.artifacts.VersionConflictStrategyType;

/**
 * by Szczepan Faber, created at: 10/4/11
 */
public class DefaultVersionConflictStrategy implements VersionConflictStrategy {

    private VersionConflictStrategyType type;

    public DefaultVersionConflictStrategy() {
        setType(latest());
    }

    public void setType(VersionConflictStrategyType type) {
        assert type != null : "Cannot set null VersionConflictStrategyType";
        this.type = type;
    }

    public VersionConflictStrategyType getType() {
        return type;
    }

    public VersionConflictStrategyType latest() {
        return VersionConflictStrategyType.LATEST;
    }

    public VersionConflictStrategyType strict() {
        return VersionConflictStrategyType.STRICT;
    }
}
