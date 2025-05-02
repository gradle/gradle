/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.tooling;
import org.gradle.api.Incubating;

/**
 * A value supplier. The Tooling API needs to be comatible with Java 7, therefore we cannot use the {@link java.util.function.Supplier} interface.
 *
 * @param <T> the type of the value
 * @since 8.12
 */
@Incubating
public interface Supplier<T> {

    /**
     * Returns the value.
     *
     * @return the value
     * @since 8.12
     */
    T get();
}
