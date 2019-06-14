/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.provider;

import org.gradle.api.Incubating;

/**
 * Represents an object that holds a value that is configurable, meaning that the value can be specified directly or derived from some source such as a {@link Provider}.
 *
 * @since 5.6
 */
@Incubating
public interface HasConfigurableValue {
    /**
     * Disallows further changes to the value of this object. Subsequent calls to methods that change the value of this object or the source from
     * which the value is derived will fail with an exception.
     *
     * <p>Note that although the value of this object will not change, the value may itself be mutable.
     * Calling this method does not guarantee that the value will become immutable, though some implementations may support this.</p>
     */
    void finalizeValue();
}
