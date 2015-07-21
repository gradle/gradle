/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.events;

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.internal.HasInternalProtocol;

/**
 * Describes an operation for which an event has occurred.
 *
 * <p>You can use {@code equals()} to determine whether 2 different descriptors refer to the same operation.</p>
 *
 * <p>The subtypes of this interface define specific types of operations, such as task execution.</p>
 *
 * @since 2.4
 */
@Incubating
@HasInternalProtocol
public interface OperationDescriptor {

    /**
     * Returns the name of the operation. This name does not necessarily uniquely identify the operation. However, the name can be used
     * by a human to disambiguate between the children of a given operation.
     *
     * @return The name of the operation.
     */
    String getName();

    /**
     * Returns a human consumable display name for the operation. This display name provides enough context for a human to uniquely identify the operation.
     *
     * @return The display name of the operation.
     */
    String getDisplayName();

    /**
     * Returns the parent operation, if any.
     *
     * @return The parent operation.
     */
    @Nullable
    OperationDescriptor getParent();

}
