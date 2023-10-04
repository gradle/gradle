/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.instance;

/**
 * The backing state for a generated view.
 *
 * Implementations should provide an equals() and hashCode() method.
 */
public interface GeneratedViewState {
    /**
     * Returns a display name to use for the view, eg as its toString() value or in error messages.
     */
    String getDisplayName();

    /**
     * Returns the value of the given property.
     */
    Object get(String name);

    /**
     * Sets the value of the given property.
     */
    void set(String name, Object value);
}
