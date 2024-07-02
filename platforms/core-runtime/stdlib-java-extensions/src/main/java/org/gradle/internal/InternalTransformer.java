/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal;

/**
 * Equivalent to {@code Transformer}, but does not declare nullability due to Java 6 restrictions.
 *
 * <p>A {@code Transformer} transforms objects of type.</p>
 *
 * <p>Implementations are free to return new objects or mutate the incoming value.</p>
 *
 * @param <OUT> The type the value is transformed to.
 * @param <IN> The type of the value to be transformed.
 */
public interface InternalTransformer<OUT, IN> {
    /**
     * Transforms the given object, and returns the transformed value.
     *
     * @param in The object to transform.
     * @return The transformed object.
     */
    OUT transform(IN in);
}
