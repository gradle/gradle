/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.provider.support;

/**
 * The return value of {@code a <OP> b} that supports custom protocol when used in the compound assignment operation, i.e. {@code a <OP>= b}.
 */
public interface CompoundAssignmentValue {
    /**
     * Called right before assigning this value to the target of the compound assignment.
     */
    void prepareForAssignment();

    /**
     * Called after this value has been assigned to the target of the compound assignment.
     */
    void assignmentCompleted();

    /**
     * Whether the value of the compound assignment expression should be replaced with null.
     * This <b>DOES NOT</b> affect the value assigned to the target of the compound assignment.
     *
     * @return {@code true} if the compound assignment expression should return null as its value
     */
    boolean shouldReplaceResultWithNull();
}
