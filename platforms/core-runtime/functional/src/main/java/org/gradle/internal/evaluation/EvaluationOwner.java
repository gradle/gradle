/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.evaluation;

/**
 * A marker interface for types that can be evaluating.
 */
public interface EvaluationOwner {
    String CIRCULAR_REFERENCE = "<CIRCULAR REFERENCE>";

    /**
     * Returns the display name of this evaluation owner, used to format the failure message.
     * <p>
     * The name is intentionally obtuse to prevent accidental collisions.
     *
     * @implSpec the default implementation returns the {@code toString()} value.
     * @return the display name of the owner
     */
    default String getEvaluationOwnerName() {
        return toString();
    }
}
