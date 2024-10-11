/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.provider.HasConfigurableValue;

public interface HasConfigurableValueInternal extends HasConfigurableValue {
    /**
     * Same semantics as {@link org.gradle.api.provider.HasConfigurableValue#finalizeValue()}, but finalizes the value of this object lazily, when the value is queried.
     * Implementations may then fail on subsequent changes, or generate a deprecation warning and ignore changes.
     */
    void implicitFinalizeValue();

    /**
     * Used for Provider API migration to not finalize values but only warn on changes to the value of this object.
     */
    void markAsUpgradedProperty();
}
