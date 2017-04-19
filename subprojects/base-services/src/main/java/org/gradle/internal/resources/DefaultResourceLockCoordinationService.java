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
import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DefaultResourceLockCoordinationService implements ResourceLockCoordinationService {
    private final Object lock = new Object();
    private final ThreadLocal<List<ResourceLockState>> currentState = new ThreadLocal<List<ResourceLockState>>() {
        @Override
        protected List<ResourceLockState> initialValue() {
            return Lists.newArrayList();
        }
    };

    @Override
    public boolean withStateLock(Transformer<ResourceLockState.Disposition, ResourceLockState> reasourceLockAction) {
        while (true) {
            DefaultResourceLockState resourceLockState = new DefaultResourceLockState();
            ResourceLockState.Disposition disposition;
            synchronized (lock) {
                try {
                    currentState.get().add(resourceLockState);
                    disposition = reasourceLockAction.transform(resourceLockState);

                    switch (disposition) {
                        case RETRY:
                            resourceLockState.reset();
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                            break;
                        case FINISHED:
                            return true;
                        case FAILED:
                            resourceLockState.reset();
                            return false;
                        default:
                            throw new IllegalArgumentException("Unhandled disposition type: " + disposition.name());
                    }
                } catch (Throwable t) {
                    resourceLockState.reset();
                    throw UncheckedException.throwAsUncheckedException(t);
                } finally {
                    currentState.get().remove(resourceLockState);
                }
            }
        }
    }

    @Override
    public ResourceLockState getCurrent() {
        if (!currentState.get().isEmpty()) {
            int numStates = currentState.get().size();
            return currentState.get().get(numStates - 1);
        } else {
            return null;
        }
    }

    @Override
    public void notifyStateChange() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private static class DefaultResourceLockState implements ResourceLockState {
        private final Set<ResourceLock> resourceLocks = Sets.newHashSet();

        @Override
        public void registerLocked(ResourceLock resourceLock) {
            resourceLocks.add(resourceLock);
        }

        Set<ResourceLock> getResourceLocks() {
            return resourceLocks;
        }

        @Override
        public void reset() {
            for (ResourceLock resourceLock : resourceLocks) {
                resourceLock.unlock();
            }
        }
    }

    /**
     * Attempts an atomic, blocking lock on the provided resource locks.
     */
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> lock(Collection<? extends ResourceLock> resourceLocks) {
        return new AcquireLocks(resourceLocks, true);
    }

    /**
     * Attempts an atomic, blocking lock on the provided resource locks.
     */
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> lock(ResourceLock... resourceLocks) {
        return lock(Arrays.asList(resourceLocks));
    }

    /**
     * Attempts an atomic, non-blocking lock on the provided resource locks.
     */
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> tryLock(Collection<? extends ResourceLock> resourceLocks) {
        return new AcquireLocks(resourceLocks, false);
    }

    /**
     * Attempts an atomic, non-blocking lock on the provided resource locks.
     */
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> tryLock(ResourceLock... resourceLocks) {
        return tryLock(Arrays.asList(resourceLocks));
    }

    /**
     * Unlocks the provided resource locks.
     */
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> unlock(Collection<? extends ResourceLock> resourceLocks) {
        return new ReleaseLocks(resourceLocks);
    }

    /**
     * Unlocks the provided resource locks.
     */
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> unlock(ResourceLock... resourceLocks) {
        return unlock(Arrays.asList(resourceLocks));
    }

    private static class AcquireLocks implements Transformer<ResourceLockState.Disposition, ResourceLockState> {
        private final Iterable<? extends ResourceLock> resourceLocks;
        private final boolean blocking;

        AcquireLocks(Iterable<? extends ResourceLock> resourceLocks, boolean blocking) {
            this.resourceLocks = resourceLocks;
            this.blocking = blocking;
        }

        @Override
        public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
            for (ResourceLock resourceLock : resourceLocks) {
                if (!resourceLock.tryLock()) {
                    return blocking ? ResourceLockState.Disposition.RETRY : ResourceLockState.Disposition.FAILED;
                }
            }
            return ResourceLockState.Disposition.FINISHED;
        }
    }

    private static class ReleaseLocks implements Transformer<ResourceLockState.Disposition, ResourceLockState> {
        private final Iterable<? extends ResourceLock> resourceLocks;

        ReleaseLocks(Iterable<? extends ResourceLock> resourceLocks) {
            this.resourceLocks = resourceLocks;
        }

        @Override
        public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
            for (ResourceLock resourceLock : resourceLocks) {
                resourceLock.unlock();
            }
            return ResourceLockState.Disposition.FINISHED;
        }
    }
}
