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
 * Currently, the only reason to use to use structured details/results objects is to expose
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
 * The following additional restrictions apply to both details and result objects:
 *
 * - Should be be practically immutable to consumers
 * - Should only expose JDK types, or other structured types only exposing JDK types
 * - Unless impractical, collection like structures should have deterministic order (e.g. sorted)
 * - Should not assume they are not accessed outside of build operation notifications
 *
 * Regarding the last point, the build scan plugin may hold on to the details and/or result
 * outside of listener callback to process it off thread.
 * It can be assumed that the objects are not held longer than is necessary to process/transform them
 * into detached representations suitable for the build scan event stream.
 *
 * Consideration should be given to the package space of the details and result types.
 * They should be housed in a logical package space, which may not be the same as the class
 * that executes the actual operation being represented, as often that is internal detail that may change.
 *
 * All types have detail objects — some empty.
 * This makes notification dispatching on the build scan plugin side easier.
 * Some types have Void result types to indicate that there is no structured result.
 * A change from a Void result type to a structured type is a compatible change,
 * the inverse however is not.
 *
 * This is because the presence of a detail object is the heuristic we use to
 * decide whether to forward an operation notification to the build scan plugin.
 * In such cases the build scan plugin does not use the empty type so we can change this
 * later and use a more definitive signal of whether to emit a notification.
 *
 * Note, the name of this type collides with {@link org.gradle.internal.progress.BuildOperationType}.
 * The latter will be renamed at some point.
 *
 * @param <D> the type of details object for the operation
 * @param <R> the type of result object for the operation
 * @since 4.0
 */
@SuppressWarnings("unused")
public interface BuildOperationType<D, R> {
}
