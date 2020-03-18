/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.state;

import org.gradle.api.Describable;
import org.gradle.api.Task;

import javax.annotation.Nullable;

/**
 * An object that represents some part of a model. This interface is mixed-in to all generated classes and should
 * not be implemented directly.
 */
public interface ModelObject {
    /**
     * Returns the display name of this object that indicates its identity, if this is known.
     */
    @Nullable
    Describable getModelIdentityDisplayName();

    /**
     * Does this type provide a useful {@link Object#toString()} implementation?
     */
    boolean hasUsefulDisplayName();

    /**
     * Returns the task that owns this object, if any.
     */
    @Nullable
    Task getTaskThatOwnsThisObject();
}
