/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A unit of work that will only be executed atomically and its outputs be reused indefinitely from an immutable workspace.
 */
public interface ImmutableUnitOfWork extends UnitOfWork {
    /**
     * Returns the {@link ImmutableWorkspaceProvider} to allocate a workspace to execution this work in.
     */
    ImmutableWorkspaceProvider getWorkspaceProvider();

    /**
     * @deprecated Immutable work should only have immutable inputs.
     *
     * TODO Move this method to {@link MutableUnitOfWork}
     */
    @Override
    @Deprecated
    default void visitMutableInputs(InputVisitor visitor) {}

    /**
     * Most immutable units of work implemented in Gradle are not worth caching
     * <p>
     * subclasses can decide to re-enable caching
     */
    @Override
    default Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
        // Disabled since enabling it introduced negative savings
        return Optional.of(NOT_WORTH_CACHING);
    }
}
