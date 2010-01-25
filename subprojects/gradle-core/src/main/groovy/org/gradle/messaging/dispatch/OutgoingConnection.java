/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.dispatch;

/**
 * Represents the outgoing half of a connection.
 */
public interface OutgoingConnection<T> extends Dispatch<T>, Addressable, AsyncStoppable {
    /**
     * Commences graceful stop of this connection. Stops accepting any more incoming messages.
     */
    void requestStop();

    /**
     * Performs a graceful stop of this connection. Stops dispatching incoming messages, and blocks until all dispatched
     * incoming messages have been handled, and all outgoing messages have been delivered.
     */
    void stop();
}
