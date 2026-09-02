/*
 * Copyright 2026 the original author or authors.
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


package org.gradle.api.internal.provider.provenance;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What is known about how a property came to be configured.
 * <p>
 * A property holds one reference to this. Measured against the gradle/gradle build, the average configured
 * property is mutated 1.18 times, so the single-mutation case is by far the common one and is represented by
 * the interned {@link MutationRecord} itself, costing no allocation at all. Only a property mutated a second
 * time promotes to a {@link MutationTrace}.
 */
public interface MutationHistory {

    /**
     * The mutations in the order they happened, oldest first.
     */
    List<MutationRecord> getRecords();

    /**
     * How many mutations happened beyond the ones retained.
     */
    int getNotRetainedCount();

    /**
     * The last mutation of the explicit value, if any.
     */
    @Nullable
    MutationRecord lastExplicit();

    /**
     * The last mutation of the convention, if any.
     */
    @Nullable
    MutationRecord lastConvention();

    /**
     * Renders this history as a sentence to append to a rejection message, or an empty string when nothing in
     * it can be attributed.
     */
    String describeForMessage();
}
