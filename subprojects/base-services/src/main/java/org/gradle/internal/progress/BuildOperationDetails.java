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

package org.gradle.internal.progress;

/**
 * Structured information about a build operation, and optionally its result.
 *
 * This interface is intentionally internal and consumed by the build scan plugin.
 *
 * The type token indicates the type of result object that the operation provides.
 * If the operation does not produce a result, extend {@link NoResultBuildOperationDetails}.
 * The producer and consumer side APIs in Gradle do not currently enforce (at compile-time or run-time)
 * That an operation with “details” provides a result, or provides a result of the right type.
 * However, the build scan plugin does assume that an operation with a details object,
 * where the result type is not {@code Void}, does provide a non-null result of the given type.
 *
 * The following rules apply to both details and result objects:
 *
 * - Types must be practically immutable to consumers
 * - Types must only expose JDK types, or other structured types only exposing JDK types
 *   - This significantly eases testing by making the objects serializable to JSON
 * - Objects should not assume they are not referenced outside of listeners
 *
 * Regarding the last point, the build scan plugin may hold on to the details and/or result
 * outside of listener callback to process it off thread.
 * It can be assumed that the objects are not held longer than is necessary to process/transform them
 * into detached representations suitable for the build scan event stream.
 *
 * @param <R> the type of result
 * @see BuildOperationDescriptor
 * @since 4.0
 */
@SuppressWarnings("unused") // R type token is not enforced right now
public interface BuildOperationDetails<R> {

}
