/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.timeout;

import org.gradle.api.Describable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Manages timeouts for threads, interrupting them if the timeout is exceeded.
 */
@ServiceScope(Scope.UserHome.class)
public interface TimeoutHandler extends Stoppable {
    /**
     * Starts a timeout for the given thread. The thread is interrupted if the given timeout is exceeded.
     * The returned {@link Timeout} object must be used to stop the timeout once the thread has completed
     * the work that this timeout was supposed to limit, otherwise it may be interrupted doing
     * some other work later.
     */
    Timeout start(Thread taskExecutionThread, Duration timeoutInMillis, Describable workUnitDescription, @Nullable BuildOperationRef buildOperationRef);

    /**
     * Stops all {@link Timeout}s created from this handler.
     */
    @Override
    void stop();
}
