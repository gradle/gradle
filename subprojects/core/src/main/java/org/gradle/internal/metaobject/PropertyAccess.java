/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.metaobject;

import java.util.Map;

/**
 * Provides dynamic access to properties of some object.
 */
public interface PropertyAccess {
    /**
     * Returns true when this object is known to have the given property.
     *
     * <p>Note that not every property is known. Some properties require an attempt to get or set their value before they are discovered.</p>
     */
    boolean hasProperty(String name);

    /**
     * Gets the value of the given property, attaching it to the given result using {@link GetPropertyResult#result(Object)}.
     *
     * <p>Use the {@link GetPropertyResult#isFound()} method to determine whether the property has been found or not.</p>
     */
    void getProperty(String name, GetPropertyResult result);

    /**
     * Sets the value of the given property. The implementation should call {@link SetPropertyResult#found()} when the property value has been set.
     *
     * <p>Use the {@link SetPropertyResult#isFound()} method to determine whether the property has been found or not.</p>
     */
    void setProperty(String name, Object value, SetPropertyResult result);

    /**
     * Returns the properties known for this object.
     */
    Map<String, ?> getProperties();

}
