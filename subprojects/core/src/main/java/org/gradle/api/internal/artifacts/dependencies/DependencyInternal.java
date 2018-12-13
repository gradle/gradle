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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.Dependency;

import javax.annotation.Nullable;
import java.util.Set;

public interface DependencyInternal extends Dependency {
    /**
     * The set of optional features we want from this dependency.
     * @return the set of optional features
     *
     * @since 5.2
     */
    @Nullable
    Set<String> getRequestedOptionalFeatures();

    /**
     * The set of features this dependency is required for. This is
     * for consumers only.
     * @return the set of features this dependency is used for
     *
     * @since 5.2
     */
    @Nullable
    Set<String> getRequiredForOptionalFeatures();
}
