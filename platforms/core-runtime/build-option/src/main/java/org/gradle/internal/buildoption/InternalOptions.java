/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.buildoption;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

/**
 * A service that determines the value for an {@link InternalOption}.
 */
@ServiceScope({Scope.CrossBuildSession.class, Scope.BuildTree.class})
public interface InternalOptions {
    /**
     * Look up the value for an {@link InternalOption}.
     *
     * @see #getBoolean(InternalFlag) for boolean options
     */
    <T extends @Nullable Object> Option.Value<T> getOption(InternalOption<T> option);

    /**
     * Look up the value for a boolean {@link InternalFlag} option.
     */
     default boolean getBoolean(InternalFlag option) {
         Boolean value = getOption(option).get();
         if (value == null) {
             throw new IllegalStateException("Option '" + option.getPropertyName() + "' is not set");
         }
         return value;
     }
}
