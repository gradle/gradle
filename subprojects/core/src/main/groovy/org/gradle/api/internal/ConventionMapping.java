/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.plugins.Convention;

import java.util.Map;
import java.util.concurrent.Callable;

import groovy.lang.Closure;

/**
 * <p>A {@code ConventionMapping} maintains the convention mappings for the properties of a particular object.</p>
 *
 * <p>Implementations should also allow mappings to be set using dynamic properties.</p>
 */
public interface ConventionMapping {
    Convention getConvention();

    void setConvention(Convention convention);

    MappedProperty map(String propertyName, ConventionValue value);

    MappedProperty map(String propertyName, Closure value);

    MappedProperty map(String propertyName, Callable<?> value);

    ConventionMapping map(Map<String, ? extends ConventionValue> properties);

    <T> T getConventionValue(T actualValue, String propertyName);

    <T> T getConventionValue(T actualValue, String propertyName, boolean isExplicitValue);

    interface MappedProperty {
        void cache();
    }
}
