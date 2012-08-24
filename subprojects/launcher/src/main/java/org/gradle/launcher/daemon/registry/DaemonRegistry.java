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

import org.gradle.messaging.remote.Address;
import org.gradle.launcher.daemon.context.DaemonContext;

import java.util.List;

/**
 * Provides access to existing daemons.
 */
public interface DaemonRegistry {

    List<DaemonInfo> getAll();
    List<DaemonInfo> getIdle();
    List<DaemonInfo> getBusy();
    
    void store(Address address, DaemonContext daemonContext, String password, boolean idle);
    void remove(Address address);
    void markBusy(Address address);
    void markIdle(Address address);

    static class EmptyRegistryException extends RuntimeException {
        public EmptyRegistryException(String message) {
            super(message);
        }
    }
}