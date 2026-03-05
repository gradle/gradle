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

package org.gradle.api.internal.groovy.support;

/**
 * Custom protocol for the value of the compound assignment expression {@code a <OP>= b}.
 */
public interface CompoundAssignmentResult {
    /**
     * Called when the assignment expression has been calculated. Implementation should discard any affinity towards its origin.
     */
    void assignmentComplete();

    /**
     * If {@code true} then the result of the {@code <OP>=} expression is replaced with {@code null}.
     * Note that this replaces the value of the whole {@code a <OP>= b}, it doesn't affect what will be stored in {@code a} (which will be this object if {@code a} is a variable).
     *
     * @return {@code true} if the result should be replaced.
     */
    boolean shouldDiscardResult();
}
