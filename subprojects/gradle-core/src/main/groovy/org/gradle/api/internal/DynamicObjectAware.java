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

import org.gradle.api.plugins.Convention;

/**
 * <p>Allows properties and method to be added to an object at run-time. Most implementations are generated at run-time
 * from existing classes, by a {@link org.gradle.api.internal.ClassGenerator} implementation.</p>
 *
 * <p>A {@code DynamicObjectAware} implementation should add meta-object methods which provide the properties and
 * methods returned by {@link #getAsDynamicObject()}.</p>
 */
public interface DynamicObjectAware {
    /**
     * Returns the convention object used by this dynamic object.
     *
     * @return The convention object. Never returns null.
     */
    Convention getConvention();

    /**
     * Sets the convention object used by this dynamic object.
     *
     * @param convention The convention object. Should not be null.
     */
    void setConvention(Convention convention);

    /**
     * Returns a {@link DynamicObject} which describes all the static and dynamic properties and methods of this
     * object.
     *
     * @return The meta-info for this object.
     */
    DynamicObject getAsDynamicObject();
}
