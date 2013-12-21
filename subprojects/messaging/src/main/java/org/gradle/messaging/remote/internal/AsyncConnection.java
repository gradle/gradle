/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.dispatch.Dispatch;

/**
 * <p>A messaging endpoint which allows push-style dispatch and receive.
 *
 * <p>Implementations must be thread-safe.
 */
public interface AsyncConnection<T> extends Dispatch<T> {
    /**
     * Dispatches a message to this connection. The implementation should not block.
     *
     * @param message The message.
     */
    void dispatch(T message);

    /**
     * Adds a handler to receive incoming messages. The handler does not need to be thread-safe. The handler should not block.
     *
     * @param handler The handler.
     */
    void dispatchTo(Dispatch<? super T> handler);
}
