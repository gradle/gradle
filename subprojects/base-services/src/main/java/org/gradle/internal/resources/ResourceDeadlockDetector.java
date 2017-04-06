/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Pair;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService.DefaultResourceLockState;
import org.gradle.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResourceDeadlockDetector {
    private final DefaultResourceLockState resourceLockState;
    private final Map<Thread, DefaultResourceLockState> waiting;
    private final Set<ResourceLockRegistry> lockRegistries;

    public ResourceDeadlockDetector(DefaultResourceLockState resourceLockState, Map<Thread, DefaultResourceLockState> waiting, Set<ResourceLockRegistry> lockRegistries) {
        this.resourceLockState = resourceLockState;
        this.waiting = waiting;
        this.lockRegistries = lockRegistries;
    }

    public List<Pair<Thread, Iterable<ResourceLock>>> checkForDeadlocks() {
        Set<ResourceLock> locksIHold = getResourceLocksByThread(Thread.currentThread());
        Map<ResourceLock, Boolean> alreadyChecked = Maps.newHashMap();
        Set<Pair<Thread, Iterable<ResourceLock>>> cycle = Sets.newLinkedHashSet();

        for (ResourceLock failedLock : resourceLockState.getResourceLockFailures()) {
            if (checkForCycle(cycle, locksIHold, failedLock, alreadyChecked)) {
                cycle.add(Pair.of(Thread.currentThread(), getHolderLocks(failedLock)));
                List<Pair<Thread, Iterable<ResourceLock>>> cycleList = Lists.newArrayList(cycle);
                Collections.reverse(cycleList);
                return cycleList;
            }
        }

        return null;
    }

    private boolean checkForCycle(final Set<Pair<Thread, Iterable<ResourceLock>>> cycle, final Set<ResourceLock> locksIHold, final ResourceLock failedLock, Map<ResourceLock, Boolean> alreadyChecked) {
        if (alreadyChecked.containsKey(failedLock)) {
            return alreadyChecked.get(failedLock);
        }

        boolean cycleExists = false;
        if (failedLock instanceof LeaseHolder) {
            cycleExists = checkForLeaseHolderCycle(cycle, locksIHold, failedLock, alreadyChecked);
        } else if (waiting.containsKey(failedLock.getOwner())) {
            DefaultResourceLockState ownerLockState = waiting.get(failedLock.getOwner());
            for (ResourceLock ownerFailedLock : ownerLockState.getResourceLockFailures()) {
                cycleExists = hasCycle(cycle, locksIHold, failedLock.getOwner(), ownerFailedLock, alreadyChecked);
                if (cycleExists) {
                    break;
                }
            }
        }

        alreadyChecked.put(failedLock, cycleExists);
        return cycleExists;
    }

    private boolean checkForLeaseHolderCycle(final Set<Pair<Thread, Iterable<ResourceLock>>> cycle, final Set<ResourceLock> locksIHold, final ResourceLock failedLock, final Map<ResourceLock, Boolean> alreadyChecked) {
        final LeaseHolder root = ((LeaseHolder) failedLock).getRoot();

        // find all of the waiting threads that hold leases for the same root
        Map<Thread, DefaultResourceLockState> waitersWithLeases = getWaitersWithLeaseRoot(root, locksIHold);

        // If all of the leases are being held by waiting threads
        if (waitersWithLeases.size() >= root.getMaxLeases()) {
            // Check to see if all of the waiters that hold leases are waiting on a lock I hold
            return CollectionUtils.every(waitersWithLeases.entrySet(), new Spec<Map.Entry<Thread, DefaultResourceLockState>>() {
                @Override
                public boolean isSatisfiedBy(final Map.Entry<Thread, DefaultResourceLockState> waiter) {
                    return CollectionUtils.any(waiter.getValue().getResourceLockFailures(), new Spec<ResourceLock>() {
                        @Override
                        public boolean isSatisfiedBy(ResourceLock waiterFailedLock) {
                            return hasCycle(cycle, locksIHold, waiter.getKey(), waiterFailedLock, alreadyChecked);
                        }
                    });
                }
            });
        } else {
            return false;
        }
    }

    private boolean hasCycle(final Set<Pair<Thread, Iterable<ResourceLock>>> cycle, final Set<ResourceLock> locksIHold, Thread thread, final ResourceLock failedLock, Map<ResourceLock, Boolean> alreadyChecked) {
        boolean leaseHolderCycle = failedLock instanceof LeaseHolder && containsLeaseRoot(locksIHold, ((LeaseHolder)failedLock).getRoot());

        if (leaseHolderCycle || locksIHold.contains(failedLock) || checkForCycle(cycle, locksIHold, failedLock, alreadyChecked)) {
            cycle.add(Pair.of(thread, getHolderLocks(failedLock)));
            return true;
        } else {
            return false;
        }
    }

    private Map<Thread, DefaultResourceLockState> getWaitersWithLeaseRoot(final LeaseHolder root, Set<ResourceLock> locksIHold) {
        Map<Thread, DefaultResourceLockState> waitersWithLeases = CollectionUtils.filter(waiting, new Spec<Map.Entry<Thread, DefaultResourceLockState>>() {
            @Override
            public boolean isSatisfiedBy(final Map.Entry<Thread, DefaultResourceLockState> waiter) {
                Set<ResourceLock> waiterLocks = getResourceLocksByThread(waiter.getKey());
                return containsLeaseRoot(waiterLocks, root);
            }
        });

        if (containsLeaseRoot(locksIHold, root)) {
            waitersWithLeases.put(Thread.currentThread(), resourceLockState);
        }

        return waitersWithLeases;
    }

    private boolean containsLeaseRoot(Set<ResourceLock> locks, final LeaseHolder root) {
        return CollectionUtils.any(locks, new Spec<ResourceLock>() {
            @Override
            public boolean isSatisfiedBy(ResourceLock resourceLock) {
                return hasSameLeaseHolderRoot(resourceLock, root);
            }
        });
    }

    private Iterable<ResourceLock> getHolderLocks(ResourceLock resourceLock) {
        if (resourceLock instanceof LeaseHolder) {
            Set<ResourceLock> holderLocks = Sets.newHashSet();
            final LeaseHolder root = ((LeaseHolder) resourceLock).getRoot();
            Set<Thread> threads = Sets.newHashSet(waiting.keySet());
            threads.add(Thread.currentThread());
            for (Thread waitingThread : threads) {
                for (ResourceLock lock : getResourceLocksByThread(waitingThread)) {
                    if (hasSameLeaseHolderRoot(lock, root)) {
                        holderLocks.add(lock);
                    }
                }
            }
            return holderLocks;
        } else {
            return Lists.newArrayList(resourceLock);
        }
    }

    private static boolean hasSameLeaseHolderRoot(ResourceLock lock, LeaseHolder root) {
        return lock instanceof LeaseHolder && ((LeaseHolder)lock).getRoot() == root;
    }

    private Set<ResourceLock> getResourceLocksByThread(Thread thread) {
        Set<ResourceLock> resourceLocks = Sets.newHashSet();
        for (ResourceLockRegistry registry : lockRegistries) {
            resourceLocks.addAll(registry.getResourceLocksByThread(thread));
        }
        return resourceLocks;
    }
}
