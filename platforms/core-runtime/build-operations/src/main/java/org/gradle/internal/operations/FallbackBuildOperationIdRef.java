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

package org.gradle.internal.operations;

import org.jspecify.annotations.Nullable;

/**
 * A {@link BuildOperationIdRef} that returns the preferred operation identifier when available,
 * and falls back to another ref otherwise.
 * <p>
 * The typical composition is {@link CurrentBuildOperationRef} (the operation the calling thread
 * is executing) with {@link RootBuildOperationRef} as the fallback, so that events produced on
 * threads that were never enrolled in an operation can still be attributed to a live operation.
 */
public class FallbackBuildOperationIdRef implements BuildOperationIdRef {

    private final BuildOperationIdRef preferred;
    private final BuildOperationIdRef fallback;

    public FallbackBuildOperationIdRef(BuildOperationIdRef preferred, BuildOperationIdRef fallback) {
        this.preferred = preferred;
        this.fallback = fallback;
    }

    /**
     * Returns the identifier of the preferred build operation, falling back to the fallback ref.
     *
     * @return the preferred identifier if available, otherwise the fallback identifier, or {@code null} if neither is available
     */
    @Nullable
    @Override
    public OperationIdentifier getId() {
        OperationIdentifier id = preferred.getId();
        return id != null ? id : fallback.getId();
    }
}
