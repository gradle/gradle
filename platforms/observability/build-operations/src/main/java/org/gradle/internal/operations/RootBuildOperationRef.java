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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

/**
 * Holds the identifier of the root build operation of the current build execution.
 * <p>
 * The current build operation is tracked per thread via {@link CurrentBuildOperationRef}.
 * Threads that were never enrolled in an operation (for example, executor threads dispatching
 * build events to user-provided listeners) have no current operation, yet events produced on
 * them still need to be attributed to a live operation. This holder exposes the root build
 * operation identifier as a fallback parent for such events, following the same approach as
 * {@code LoggingBuildOperationProgressBroadcaster} uses for logging output.
 * <p>
 * The identifier is captured when the root build operation starts and is overwritten by each
 * subsequent root build operation within the same cross-build-session state. It is never
 * cleared, and it is {@code null} until the first build operation starts.
 * <p>
 * Worker processes have no root build operation; there, the operation of the work request being
 * processed serves as the fallback attribution target instead (see {@code WorkRequestBuildOperationRef}).
 */
@ServiceScope(Scope.CrossBuildSession.class)
public class RootBuildOperationRef implements BuildOperationIdRef {

    private volatile @Nullable OperationIdentifier rootBuildOperationId;

    /**
     * Captures the identifier of the root build operation.
     *
     * @param rootBuildOperationId the identifier of the root build operation that just started, if any
     */
    public void set(@Nullable OperationIdentifier rootBuildOperationId) {
        this.rootBuildOperationId = rootBuildOperationId;
    }

    /**
     * Returns the identifier of the root build operation.
     *
     * @return the identifier captured for the current build execution, or {@code null} if no root build operation has started yet
     */
    @Nullable
    @Override
    public OperationIdentifier getId() {
        return rootBuildOperationId;
    }
}
