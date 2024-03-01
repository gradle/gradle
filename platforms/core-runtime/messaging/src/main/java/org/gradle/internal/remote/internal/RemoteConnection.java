/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.remote.internal;

import javax.annotation.Nullable;

/**
 * <p>A messaging end-point with some remote, or otherwise unreliable, peer.</p>
 *
 * <p>This interface simply specializes the exceptions thrown by the methods of this connection.</p>
 */
public interface RemoteConnection<T> extends Connection<T> {
    /**
     * {@inheritDoc}
     *
     * @throws MessageIOException On failure to dispatch the message to the peer.
     */
    @Override
    void dispatch(T message) throws MessageIOException;

    void flush() throws MessageIOException;

    /**
     * {@inheritDoc}
     * @throws MessageIOException On failure to receive the message from the peer.
     */
    @Override
    @Nullable
    T receive() throws MessageIOException;
}
