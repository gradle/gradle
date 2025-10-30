/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.caching.configuration;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;


/**
 * Extension to {@link BuildCache} that exposes the {@code push} flag as a
 * lazy {@link org.gradle.api.provider.Property}.
 *
 * @since 9.3.0
 */
@Incubating
public interface BuildCachePushProperty {

    /**
     * Lazy, convention-backed 'push' flag.
     *
     * @since 9.3.0
     */
    @Incubating
    Property<Boolean> getPush();
}
