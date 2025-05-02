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
package org.gradle.api;

/**
 * Types can implement this interface when they provide a human-readable display name.
 * It is strongly encouraged to compute this display name lazily: computing a display name,
 * even if it's only a string concatenation, can take a significant amount of time during
 * configuration for something that would only be used, typically, in error messages.
 *
 * @since 3.4
 */
public interface Describable {
    /**
     * Returns the display name of this object. It is strongly encouraged to compute it
     * lazily, and cache the value if it is expensive.
     * @return the display name
     */
    String getDisplayName();
}
