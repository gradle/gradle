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

import java.util.List;

/**
 * A {@code Disconnection} is used with the {@link DisconnectAwareConnection} to provide information about
 * the connection at the time that the disconnection is detected.
 *
 * @param <T> The type of object transmitted on the connection that was disconnected.
 */
public interface Disconnection<T> {

    /**
     * The connection that has been disconnected by the other side.
     */
    Connection<T> getConnection();

    /**
     * Any messages that were sent by the other side but not yet collected by invocation of {@link #receive()}
     */
    List<T> getUncollectedMessages();
}