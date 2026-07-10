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

import org.gradle.internal.DisplayName;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.function.Supplier;

/**
 * A factory for {@link CalculatedValue}.
 */
@ServiceScope(Scope.BuildSession.class)
public interface CalculatedValueFactory {
    /**
     * Creates a calculated value that has no dependencies and that does not access any mutable model state.
     */
    <T> CalculatedValue<T> create(DisplayName displayName, Supplier<? extends T> supplier);

    /**
     * A convenience to create a calculated value that has already been produced.
     * <p>
     * For example, the value might have been restored from the configuration cache.
     */
    <T> CalculatedValue<T> create(DisplayName displayName, T value);

}
