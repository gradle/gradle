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

package org.gradle.internal.operations;

/**
 * A type token for a type of a rich build operation that provides structured metadata.
 *
 * The type tokens indicate the type of details and result objects associated with operations of this type.
 * The type token should be a non-instantiable and non extensible class.
 *
 * The producer side APIs in Gradle do not currently enforce (at compile-time or run-time) details or result types.
 * Over time, they will be evolved to enforce usage of correct types.
 *
 * Currently, the only reason to use structured details/results objects is to expose
 * information to the build scan plugin.
 * The build scan plugin will use the details and result types,
 * but will not link against the containing BuildOperationType type.
 * It is only used on the producer side.
 *
 * The details object should effectively provide the identifying context for the operation.
 * This is information that is known before the operation is executed.
 *
 * The result object should convey the outcome.
 * This is information that is known after the operation is executed.
 *
 * These details and result types need to maintain backwards binary compatibility.
 * They should be interfaces with minimal dependencies, and should avoid internal Gradle types.
 * The convention for these types is to make them inner types of the BuildOperationType token type,
 * named {@code Details} and {@code Result}.
 * However, this is not required – the types can go anywhere.
 *
 * The information exposed by details and results objects ultimately needs to be serialized.
 * As such, these objects should be designed for serialization to non Java formats (e.g. JSON).
 *
 * - Should be practically immutable
 * - Should only expose primitive(ish) JDK types, or other structured types only exposing JDK types
 * - Collection like structures should have deterministic order - either sorted, or meaningful
 * - Should expose either java.util.List or java.util.Map over specialised collection types
 *
 * Implementations can assume that their getters will be called at-most-once.
 *
 * The lifecycle of details and result objects are effectively undetermined.
 * The build scan plugin will retain the objects for a short time after the operation has
 * completed, in order to "process" them on a separate thread.
 * It can be assumed that this processing happens relatively quickly,
 * after which the objects are no longer retained.
 *
 * Consideration should be given to the package space of the details and result types.
 * They should be housed in a logical package space, which may not be the same as the class
 * that executes the actual operation being represented, as often that is internal detail that may change.
 *
 * @param <D> the type of details object for the operation
 * @param <R> the type of result object for the operation
 * @since 4.0
 */
@SuppressWarnings("unused")
public interface BuildOperationType<D, R> {
}
