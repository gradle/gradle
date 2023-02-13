/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal;

/**
 * Mutable value type for which mutation can be disabled at a certain point in time,
 * by calling the {@code disableFurtherMutations()} method.
 * <p>
 * After {@code disableFurtherMutations()} has been called, any subsequent calls to methods that mutate
 * the value in any way will fail by throwing an {@code IllegalStateException}.
 */
public interface FinalizableValue {

    /**
     * Disallows further changes to the value represented by this type.
     * <p>
     * Subsequent calls to methods that mutate the value in any way will fail by throwing an {@code IllegalStateException}.
     */
    void preventFromFurtherMutation();

}
