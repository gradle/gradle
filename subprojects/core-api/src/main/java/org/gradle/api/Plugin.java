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
package org.gradle.api;

/**
 * <p>A <code>Plugin</code> represents an extension to Gradle. A plugin applies some configuration to a target object.
 * Usually, this target object is a {@link org.gradle.api.Project}, but plugins can be applied to any type of
 * objects.</p>
 *
 * @param <T> The type of object which this plugin can configure.
 */
public interface Plugin<T> {
    /**
     * Apply this plugin to the given target object.
     *
     * @param target The target object
     */
    void apply(T target);
}
