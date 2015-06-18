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

package org.gradle.tooling.internal.protocol.events;

import org.gradle.tooling.internal.protocol.InternalProtocolInterface;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 2.5
 */
public interface InternalOperationDescriptor extends InternalProtocolInterface {

    /**
     * Returns an id that uniquely identifies the operation.
     */
    Object getId();

    /**
     * Returns the name of the operation, relative to its parent. This name should generally uniquely identify to
     * a human each child operation of a given operation.
     */
    String getName();

    /**
     * Returns a human consumable display name for the operation. This display name should generally provide enough context
     * to uniquely identify the operation to a human.
     *
     * @return The display name of the operation
     */
    String getDisplayName();

    /**
     * Returns the id of the parent of the operation, if any.
     *
     * @return The id of the parent of the operation, can be null
     */
    Object getParentId();
}
