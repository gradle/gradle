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
package org.gradle.api.internal;

import groovy.lang.Closure;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

/**
 * <p>A {@code ConventionMapping} maintains the convention mappings for the properties of a particular object.</p>
 *
 * <p>Implementations should also allow mappings to be set using dynamic properties.</p>
 */
public interface ConventionMapping {

    MappedProperty map(String propertyName, Closure<?> value);

    MappedProperty map(String propertyName, Callable<?> value);

    /**
     * Mark a property as ineligible for convention mapping.
     */
    void ineligible(String propertyName);

    @Nullable
    <T> T getConventionValue(@Nullable T actualValue, String propertyName, boolean isExplicitValue);

    interface MappedProperty {
        void cache();
    }
}
