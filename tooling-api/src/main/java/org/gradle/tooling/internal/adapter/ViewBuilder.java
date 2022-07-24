/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.adapter;

import javax.annotation.Nullable;

public interface ViewBuilder<T> {

    /**
     * Mixes the given object into all views of the given type created using {@link #build(Object)}.
     * Applied to all views reachable from created views. The mix-in object should be serializable.
     *
     * When a given method cannot be found on the source object for a view, the mix-in object is searched for a compatible method.
     * For a getter method, the mix-in may also provide a method that takes the view as a parameter.
     *
     * @return this
     */
    ViewBuilder<T> mixInTo(Class<?> targetType, Object mixIn);

    /**
     * Mixes the given type into all views of the given type created using {@link #build(Object)}.
     * Applied to all views reachable from created views.
     *
     * An instance of the class is created for each view of the given type that is created. The class should have a constructor that accepts the view as a parameter.
     * When a given method cannot be found on the source object for a view, the mix-in object is searched for a compatible method.
     *
     * @return this
     */
    ViewBuilder<T> mixInTo(Class<?> targetType, Class<?> mixInType);

    /**
     * Creates a view for the given source object. Returns null when source object is null.
     */
    T build(@Nullable Object sourceObject);
}
