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
 * @see BuildOperationDescriptor
 * @param <R> the type of result that is associated with this kind of build operation
 * @since 4.0
 */
public interface BuildOperationDetails<R> {

    /**
     * A convenience method that automatically casts the result of an
     * operation to the type expected for this kind of operation.
     */
    R getResult(OperationFinishEvent finishEvent);
}
