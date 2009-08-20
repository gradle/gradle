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

package org.gradle.api.artifacts;

import groovy.lang.Closure;
import org.gradle.api.Transformer;

/**
 * <p>A {@code IvyObjectBuilder} builds Ivy domain objects of type {@code T}. You can influence the construction of the
 * Ivy objects by adding transformers to this builder. A transformer can either be a closure, or a {@link Transformer}
 * implementation. The transformers are called in the order added.</p>
 */
public interface IvyObjectBuilder<T> {
    /**
     * <p>Adds a transformer to this builder.</p>
     *
     * @param transformer The transformer to add.
     */
    void addIvyTransformer(Transformer<T> transformer);

    /**
     * <p>Adds a transformation closure to this builder. The closure is passed the object to transform as a parameter.
     * The closure can return an object of type T, which will be used as the transformed value. The object to transform
     * is also set as the delegate of the closure.</p>
     *
     * @param transformer The transformation closure to add.
     */
    void addIvyTransformer(Closure transformer);
}
