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

package org.gradle.internal.model;

/**
 * Interns values, similar to {@link String#intern()}.
 */
public interface InMemoryInterner<T> {

    /**
     * Intern the provided instance. If this interner has already come across
     * an instance equal to the provided value, it will return the prior
     * instance. Otherwise, the provided instance is returned.
     * <p>
     * Returned values may be compared with instance equality ({@code ==})
     *
     * @param value The value to intern.
     *
     * @return An instance equal to {@code value}.
     */
    T intern(T value);

    /**
     * Invalidates the interner, clearing all entries.
     */
    void invalidate();

}
