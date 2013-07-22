/*
 * Copyright 2007-2008 the original author or authors.
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

/**
 * <p>Allows default values for the properties of this object to be declared. Most implementations are generated at
 * run-time from existing classes, by a {@link org.gradle.api.internal.ClassGenerator} implementation.</p>
 *
 * <p>Each getter of an {@code IConventionAware} object should use the mappings to determine the value for the property,
 * when no value has been explicitly set for the property.</p>
 */
public interface IConventionAware {

    /**
     * Returns the convention mapping for the properties of this object. The returned mapping object can be used to
     * manage the mapping for individual properties.
     *
     * @return The mapping. Never returns null.
     */
    ConventionMapping getConventionMapping();
}
