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
 * Strongly typed details of a build operation, for listeners that want to do deeper analysis
 * instead of just showing display names.
 *
 * This interface is intentionally internal and consumed by the build scan plugin.
 *
 * The return type token is currently not used/enforced.
 * We will iterate on the producer side API to compile time enforce it.
 *
 * @param <R> the type of result
 * @see BuildOperationDescriptor
 * @since 4.0
 */
@SuppressWarnings("unused") // R type token is not enforced right now
public interface BuildOperationDetails<R> {

}
