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

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.messaging.remote.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private List<EmbeddedDaemonEntry> entries = new CopyOnWriteArrayList<EmbeddedDaemonEntry>();

    private final Spec<EmbeddedDaemonEntry> allSpec = new Spec<EmbeddedDaemonEntry>() {
        public boolean isSatisfiedBy(EmbeddedDaemonEntry entry) {
            return entry.status != null;
        }
    };

    private final Spec<EmbeddedDaemonEntry> idleSpec = Specs.<EmbeddedDaemonEntry>and(allSpec, new Spec<EmbeddedDaemonEntry>() {
        public boolean isSatisfiedBy(EmbeddedDaemonEntry entry) {
            return entry.status.isIdle();
        }
    });

    private final Spec<EmbeddedDaemonEntry> busySpec = Specs.<EmbeddedDaemonEntry>and(allSpec, new Spec<EmbeddedDaemonEntry>() {
        public boolean isSatisfiedBy(EmbeddedDaemonEntry entry) {
            return !entry.status.isIdle();
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

    private List<DaemonStatus> statusesOfEntriesMatching(Spec<EmbeddedDaemonEntry> spec) {
        List<DaemonStatus> matches = new ArrayList<DaemonStatus>();
        for (EmbeddedDaemonEntry entry : entries) {
            if (spec.isSatisfiedBy(entry)) {
                matches.add(entry.status);
            }
        }

        return matches;
    }

    public Entry newEntry() {
        EmbeddedDaemonEntry entry = new EmbeddedDaemonEntry();
        entries.add(entry);
        return entry;
    }

    private class EmbeddedDaemonEntry implements Entry {

        private DaemonStatus status;

        private void assertHasStatus() {
            if (status == null) {
                throw new IllegalStateException("store() must be called before markBusy() or markIdle()");
            }
        }

        public void markBusy() {
            assertHasStatus();
            status.setIdle(false);
        }

        public void markIdle() {
            assertHasStatus();
            status.setIdle(true);
        }

        public void store(Address address) {
            if (status != null) {
                throw new IllegalStateException("store() has already been called for this entry");
            }

            status = new DaemonStatus(address);
        }

        public void remove() {
            this.status = null;
            entries.remove(this);
        }
    }

}