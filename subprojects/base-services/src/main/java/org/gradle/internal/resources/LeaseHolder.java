/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.resources;

import com.google.common.base.Optional;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class LeaseHolder {
    private final Set<Lease> availableLeases = new LinkedHashSet<Lease>();

    public LeaseHolder(int maxWorkerCount) {
        for (int i=0; i<maxWorkerCount; i++) {
            availableLeases.add(new Lease(i));
        }
    }

    public Optional<Lease> grantLease() {
        if (availableLeases.isEmpty()) {
            return Optional.absent();
        } else {
            return Optional.of(pop());
        }
    }

    private Lease pop() {
        Iterator<Lease> iterator = availableLeases.iterator();
        Lease next = iterator.next();
        iterator.remove();
        return next;
    }

    private void push(Lease lease) {
        availableLeases.add(lease);
    }

    public void releaseLease(Lease lease) {
        push(lease);
    }

    static class Lease {
        private final int leaseNumber;

        private Lease(int leaseNumber) {
            this.leaseNumber = leaseNumber;
        }

        public int getLeaseNumber() {
            return leaseNumber;
        }
    }
}
