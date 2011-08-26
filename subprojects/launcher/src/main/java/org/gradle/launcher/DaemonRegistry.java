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

package org.gradle.launcher;

import org.gradle.messaging.remote.Address;

import java.util.List;

/**
 * Provides access to existing daemons.
 */
public interface DaemonRegistry {

    List<DaemonStatus> getAll();
    List<DaemonStatus> getIdle();
    List<DaemonStatus> getBusy();
    
    /**
     * Create a new entry object, but do not store it in the registry.
     * 
     * Callers should use the store() method on the returned entry object to add this entry to the registry.
     */
    Entry newEntry();
    
    /**
     * An entry in a daemon registry (i.e. info about a particular daemon)
     */
    public interface Entry {
        public void markBusy();
        public void markIdle();
        
        /**
         * Add this entry to the registry that created it, storing address as the address for the daemon.
         */
        public void store(Address address);
        
        /**
         * Remove this entry from the registry that it belongs to.
         */
        public void remove();
    }
}
