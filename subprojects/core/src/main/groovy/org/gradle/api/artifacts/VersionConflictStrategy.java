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

package org.gradle.api.artifacts;

import groovy.lang.Closure;

/**
 * Defines the strategy in case there's a dependency version conflict.
 */
public interface VersionConflictStrategy {

    /**
     * Configures current strategy type
     *
     * @param type type to set
     */
    public void setType(VersionConflictStrategyType type);

    /**
     * gets current version conflict strategy type
     */
    public VersionConflictStrategyType getType();

    /**
     * use the latest of conflicting versions and move on
     */
    public VersionConflictStrategyType latest();

    /**
     * fail eagerly on conflict
     */
    public VersionConflictStrategyType strict(Closure closure);
}
