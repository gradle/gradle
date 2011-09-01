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
package org.gradle.launcher.daemon;

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.concurrent.CompositeStoppable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A daemon registry for daemons running in the same JVM.
 * <p>
 * This implementation is thread safe in that its getAll(), getIdle() and getBusy() methods are expected to be called from “consumer” threads,
 * while the newEntry() method is expected to be called by “producer” threads.
 * <p>
 * The collections returned by the consumer methods do not return live collections so may not reflect the precise state of the registry
 * by the time they are returned to the caller. Clients must therefore be prepared for this and expect connection failures, either through
 * the endpoint disappearing or becoming busy between asking for idle daemons and trying to connect.
 */
public class EmbeddedDaemonRegistry implements DaemonRegistry {

    private final Map<Address, DaemonStatus> statuses = new ConcurrentHashMap<Address, DaemonStatus>();
    private final List<Daemon> daemons = new ArrayList<Daemon>();
    private final Lock daemonsLock = new ReentrantLock();

    private final Spec<DaemonStatus> allSpec = new Spec<DaemonStatus>() {
        public boolean isSatisfiedBy(DaemonStatus entry) {
            return true;
        }
    };

    private final Spec<DaemonStatus> idleSpec = Specs.and(allSpec, new Spec<DaemonStatus>() {
        public boolean isSatisfiedBy(DaemonStatus status) {
            return status.isIdle();
        }
    });

    private final Spec<DaemonStatus> busySpec = Specs.and(allSpec, new Spec<DaemonStatus>() {
        public boolean isSatisfiedBy(DaemonStatus status) {
            return !status.isIdle();
        }
    });

    public List<DaemonStatus> getAll() {
        return statusesOfEntriesMatching(allSpec);
    }

    public List<DaemonStatus> getIdle() {
        return statusesOfEntriesMatching(idleSpec);
    }

    public List<DaemonStatus> getBusy() {
        return statusesOfEntriesMatching(busySpec);
    }

    public void store(Address address) {
        statuses.put(address, new DaemonStatus(address));
    }

    public void remove(Address address) {
        statuses.remove(address);
    }

    public void markBusy(Address address) {
        synchronized (statuses) {
            statuses.get(address).setIdle(false);
        }
    }

    public void markIdle(Address address) {
        synchronized (statuses) {
            statuses.get(address).setIdle(true);
        }
    }

    private List<DaemonStatus> statusesOfEntriesMatching(Spec<DaemonStatus> spec) {
        List<DaemonStatus> matches = new ArrayList<DaemonStatus>();
        for (DaemonStatus status : statuses.values()) {
            if (spec.isSatisfiedBy(status)) {
                matches.add(status);
            }
        }

        return matches;
    }

    /**
     * Returns all daemons started in this registry since construction or most recent stopDaemons().
     * <p>
     * The returned daemons are not guaranteed to be running as they may have been stopped individually.
     */
    public List<Daemon> getDaemons() {
        daemonsLock.lock();
        try {
            return new ArrayList(daemons);
        } finally {
            daemonsLock.unlock();
        }
    }
    
    public void startDaemon(Daemon daemon) {
        daemonsLock.lock();
        try {
            daemons.add(daemon);
        } finally {
            daemonsLock.unlock();
        }

        daemon.start();
    }

    public void stopDaemons() {
        List<Daemon> daemonsToStop;
        
        daemonsLock.lock();
        try {
            daemonsToStop = new ArrayList(daemons);
            daemons.clear();
        } finally {
            daemonsLock.unlock();
        }
        
        new CompositeStoppable(daemonsToStop).stop();
    }
}