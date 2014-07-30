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
import org.gradle.tooling.internal.consumer.DefaultCancellationToken;

/**
 * Object that creates {@link CancellationToken}, and also issues cancellation request passed to this token.
 *
 * <p>This is a client side part of cancellation support:
 * Tooling API clients can create cancellation token using instance of this class and send cancel request to it
 * when needed.</p>
 * <p>This class is thread safe.</p>
 *
 * @since 2.1
 */
@Incubating
public final class CancellationTokenSource {
    private DefaultCancellationToken token = new DefaultCancellationToken();

    public CancellationTokenSource() {
    }

    // TODO exception handling from callbacks (aggregate into one exception and rethrow?)
    /**
     * Initiates cancel request that is passed to {@link org.gradle.tooling.CancellationToken}
     * where it will be handled.
     * <p>Any callbacks registered with the token will be executed.</p>
     * <p>It is assumed that the implementation will do 'best-effort' attempt to perform cancellation.
     * This method returns immediately and if the cancellation is successful the cancelled operation
     * will notify its {@link org.gradle.tooling.ResultHandler#onFailure(GradleConnectionException)}
     * with appropriate exception describing how it was cancelled.
     * </p>
     */
    public void cancel() {
        token.doCancel();
    }

    /**
     * Returns a token associated with this {@code CancellationTokenSource}.
     * Always returns the same instance.
     *
     * @return The cancellation token.
     */
    public CancellationToken token() {
        return token;
    }
}
