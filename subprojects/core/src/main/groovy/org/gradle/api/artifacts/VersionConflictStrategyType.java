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

/**
 * The type of the conflict strategy
 * <p>
 * by Szczepan Faber, created at: 10/4/11
 */
public enum VersionConflictStrategyType {
    /**
     * use the latest of conflicting versions and move on
     */
    LATEST,

    /**
     * fail eagerly on conflict
     */
    STRICT
}