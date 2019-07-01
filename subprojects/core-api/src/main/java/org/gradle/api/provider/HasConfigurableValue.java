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
 * Represents an object that holds a value that is configurable, meaning that the value or some source for the value, such as a {@link Provider},
 * can be specified directly on the object.
 *
 * <p>This interface provides methods to manage the lifecycle of the value. Using these methods, the value of the object can be <em>finalized</em>, which means that the value will
 * no longer change. Note that this is not the same as an <em>immutable</em> value. You can think of a finalized value as similar to a {@code final} variable in Java. While the value does not change,
 * the value itself may still be mutable.</p>
 *
 * <p>When a task property has a value of this type, it will be implicitly finalized when the task starts execution, to prevent accidental changes to the task parameters as the task runs.</p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.</p>
 *
 * @since 5.6
 */
@Incubating
public interface HasConfigurableValue {
    /**
     * Calculates the final value of this object and disallows further changes to this object.
     *
     * <p>To calculate the final value of this object, any source for the value is queried and the result used as the final value for this object. The source is discarded.</p>
     *
     * <p>Subsequent attempts to change the value of this object or the source from which the value is derived will fail with an exception.
     *
     * <p>Note that although the value of this object will no longer change, the value may itself be mutable.
     * Calling this method does not guarantee that the value will become immutable, though some implementations may support this.</p>
     */
    void finalizeValue();

    /**
     * Disallows further direct changes to this object.
     *
     * <p>This differs from {@link #finalizeValue()} in that it does not calculate the final value of this object, and so any source for the value will continue to be used until
     * the value is finalized.</p>
     */
    void disallowChanges();
}
