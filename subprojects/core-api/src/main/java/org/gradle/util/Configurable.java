/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.internal.Cast;

/**
 * An object that can be configured.
 *
 * @param <T> the return type.
 */
public interface Configurable<T> {
    T configure(Closure cl);

    /**
     * Configures this object with the given action.
     *
     * @param action the action to configure with
     * @return this
     *
     * @since 8.0
     */
    default T configure(Action<? super T> action) {
        T target = Cast.uncheckedCast(this);
        action.execute(target);
        return target;
    }
}
