/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution.history;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

/**
 * Captures the state of a {@link org.gradle.internal.execution.UnitOfWork} after it has been executed.
 *
 * Execution here might also mean being up-to-date or loaded from cache.
 */
public interface AfterExecutionState extends InputExecutionState, OutputExecutionState {
    @Override
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties();

    /**
     * The origin metadata of the outputs captured.
     *
     * This might come from the current execution, or a previous one.
     *
     * @see #isReused()
     */
    OriginMetadata getOriginMetadata();

    /**
     * Whether the outputs come from a previous execution.
     */
    boolean isReused();
}
