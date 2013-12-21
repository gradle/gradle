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

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.messaging.remote.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<Address, DaemonInfo> daemonInfos = new ConcurrentHashMap<Address, DaemonInfo>();
    private final Spec<DaemonInfo> allSpec = new Spec<DaemonInfo>() {
        public boolean isSatisfiedBy(DaemonInfo entry) {
            return true;
        }
    };

    @SuppressWarnings("unchecked")
    private final Spec<DaemonInfo> idleSpec = Specs.<DaemonInfo>and(allSpec, new Spec<DaemonInfo>() {
        public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
            return daemonInfo.isIdle();
        }
    });

    @SuppressWarnings("unchecked")
    private final Spec<DaemonInfo> busySpec = Specs.<DaemonInfo>and(allSpec, new Spec<DaemonInfo>() {
        public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
            return !daemonInfo.isIdle();
        }
    });

    public List<DaemonInfo> getAll() {
        return daemonInfosOfEntriesMatching(allSpec);
    }

    public List<DaemonInfo> getIdle() {
        return daemonInfosOfEntriesMatching(idleSpec);
    }

    public List<DaemonInfo> getBusy() {
        return daemonInfosOfEntriesMatching(busySpec);
    }

    public void store(Address address, DaemonContext daemonContext, String password, boolean idle) {
        daemonInfos.put(address, new DaemonInfo(address, daemonContext, password, idle));
    }

    public void remove(Address address) {
        daemonInfos.remove(address);
    }

    public void markBusy(Address address) {
        synchronized (daemonInfos) {
            daemonInfos.get(address).setIdle(false);
        }
    }

    public void markIdle(Address address) {
        synchronized (daemonInfos) {
            daemonInfos.get(address).setIdle(true);
        }
    }

    private List<DaemonInfo> daemonInfosOfEntriesMatching(Spec<DaemonInfo> spec) {
        List<DaemonInfo> matches = new ArrayList<DaemonInfo>();
        for (DaemonInfo daemonInfo : daemonInfos.values()) {
            if (spec.isSatisfiedBy(daemonInfo)) {
                matches.add(daemonInfo);
            }
        }

        return matches;
    }
}