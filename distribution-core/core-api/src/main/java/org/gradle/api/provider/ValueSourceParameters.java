/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.provider;

import org.gradle.api.Incubating;

/**
 * Marker interface for parameter objects to {@link ValueSource}s.
 *
 * <p>
 * Parameter types should be interfaces, only declaring getters for {@link org.gradle.api.provider.Property}-like objects.
 * </p>
 * <pre class='autoTested'>
 * public interface MyParameters extends ValueSourceParameters {
 *     Property&lt;String&gt; getStringParameter();
 * }
 * </pre>
 *
 * @since 6.1
 */
@Incubating
public interface ValueSourceParameters {
    /**
     * Used for sources without parameters.
     *
     * @since 6.1
     */
    @Incubating
    final class None implements ValueSourceParameters {
        private None() {
        }
    }
}
