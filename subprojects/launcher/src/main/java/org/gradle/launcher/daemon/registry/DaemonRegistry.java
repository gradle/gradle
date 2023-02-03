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

package org.gradle.launcher.daemon.registry;

import javax.annotation.concurrent.ThreadSafe;
import org.gradle.internal.remote.Address;

import java.util.Collection;
import java.util.List;

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.*;

/**
 * Provides access to existing daemons.
 *
 * Implementations should be thread-safe.
 */
@ThreadSafe
public interface DaemonRegistry {

    List<DaemonInfo> getAll();
    List<DaemonInfo> getIdle();
    List<DaemonInfo> getNotIdle();
    List<DaemonInfo> getCanceled();

    void store(DaemonInfo info);
    void remove(Address address);
    void markState(Address address, State state);

    void storeStopEvent(DaemonStopEvent stopEvent);
    List<DaemonStopEvent> getStopEvents();
    void removeStopEvents(Collection<DaemonStopEvent> stopEvents);

    static class EmptyRegistryException extends RuntimeException {
        public EmptyRegistryException(String message) {
            super(message);
        }
    }
}
