/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component.model;

import javax.annotation.Nullable;

/**
 * State for a component that is specific to a particular dependency graph resolution.
 *
 * @see ComponentGraphResolveState for graph independent state for the component.
 */
public interface ComponentGraphSpecificResolveState {
    ComponentGraphSpecificResolveState EMPTY_STATE = new ComponentGraphSpecificResolveState() {
        @Nullable
        @Override
        public String getRepositoryName() {
            return null;
        }
    };

    @Nullable
    String getRepositoryName();
}
