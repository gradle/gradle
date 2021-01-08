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

import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;

/**
 * Represents an object that may be owned by some model object. This is mixed-in to generated classes and may
 * also be implemented directly.
 */
public interface OwnerAware {
    /**
     * Notifies this object that it now has an owner associated with it.
     *
     * @param owner The owner object, if any.
     * @param displayName The display name for this object.
     */
    void attachOwner(@Nullable ModelObject owner, DisplayName displayName);
}
