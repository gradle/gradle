/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling;

import org.gradle.api.Incubating;

/**
 * Token that propagates notification that an operation should be cancelled.
 * <p>Tooling API implementation is expected to monitor the status of this token to perform the cancellation.</p>
 *
 * @since 2.1
 */
@Incubating
public interface CancellationToken {
    /**
     * Gets whether cancellation has been requested for this token.
     * @return Cancellation status.
     */
    boolean isCancellationRequested();
    /**
     * Registers a callback notified synchronously when token is cancelled.
     *
     * <p>The callback method should be fast because it is called synchronously when cancel is requested
     * and therefore the call to {@link CancellationTokenSource#cancel()} does not return until the callback returns.</p>
     *
     * <p>If the token is already cancelled the handler will be called synchronously before this method returns.</p>
     * <p>Implementation note: it is not guaranteed that all handlers will be executed if on of them throws an exception upon its execution.</p>
     *
     * @param cancellationHandler callback executed when cancel is requested.
     * @return current state of cancellation request before callback was added.
     */
    boolean addCallback(Runnable cancellationHandler);
}
