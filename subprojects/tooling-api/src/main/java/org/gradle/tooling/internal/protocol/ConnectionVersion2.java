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
package org.gradle.tooling.internal.protocol;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 */
public interface ConnectionVersion2 {
    /**
     * Stops this connection, blocking until complete.
     */
    void stop();

    /**
     * Returns a display name for this connection, which can be used in logging and error reporting.
     *
     * @return The display name.
     */
    String getDisplayName();

    /**
     * Fetches a snapshot of the model for the project. This method returns immediately.
     *
     * @param type The type of model to fetch.
     * @param handler The handler to pass the model to.
     * @param <T> The type of model to fetch.
     * @throws UnsupportedOperationException When the given model type is not supported.
     * @throws IllegalStateException When this connection has been stopped.
     */
    <T extends ProjectVersion2> void getModel(Class<T> type, ResultHandlerVersion1<? super T> handler) throws UnsupportedOperationException, IllegalStateException;
}
