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

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.internal.MutableReference;
import org.gradle.internal.UncheckedException;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultResourceLockCoordinationService implements ResourceLockCoordinationService, Closeable {
    private final Object lock = new Object();
    private final Set<Action<ResourceLock>> releaseHandlers = new LinkedHashSet<Action<ResourceLock>>();
    private final ThreadLocal<List<ResourceLockState>> currentState = new ThreadLocal<List<ResourceLockState>>() {
        @Override
        protected List<ResourceLockState> initialValue() {
            return Lists.newArrayList();
        }
    };

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            if (!releaseHandlers.isEmpty()) {
                throw new IllegalStateException("Some lock release listeners have not been removed.");
            }
        }
    }

    @Override
    public void assertHasStateLock() {
        synchronized (lock) {
            if (getCurrent() == null) {
                throw new IllegalStateException();
            }
        }
    }

    @Override
    public void addLockReleaseListener(Action<ResourceLock> listener) {
        synchronized (lock) {
            releaseHandlers.add(listener);
        }
    }

    @Override
    public void removeLockReleaseListener(Action<ResourceLock> listener) {
        synchronized (lock) {
            releaseHandlers.remove(listener);
        }
    }

    @Override
    public void withStateLock(final Runnable action) {
        withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                action.run();
                return ResourceLockState.Disposition.FINISHED;
            }
        });
    }

    @Override
    public <T> T withStateLock(final Supplier<T> action) {
        final MutableReference<T> result = MutableReference.empty();
        withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                result.set(action.get());
                return ResourceLockState.Disposition.FINISHED;
            }
        });
        return result.get();
    }

    @Override
    public boolean withStateLock(Transformer<ResourceLockState.Disposition, ResourceLockState> stateLockAction) {
        while (true) {
            DefaultResourceLockState resourceLockState = new DefaultResourceLockState();
            ResourceLockState.Disposition disposition;
            synchronized (lock) {
                try {
                    currentState.get().add(resourceLockState);
                    disposition = stateLockAction.transform(resourceLockState);

                    switch (disposition) {
                        case RETRY:
                            resourceLockState.releaseLocks();
                            maybeNotifyStateChange(resourceLockState);
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                            break;
                        case FINISHED:
                            maybeNotifyStateChange(resourceLockState);
                            return true;
                        case FAILED:
                            resourceLockState.releaseLocks();
                            return false;
                        default:
                            throw new IllegalArgumentException("Unhandled disposition type: " + disposition.name());
                    }
                } catch (Throwable t) {
                    resourceLockState.releaseLocks();
                    throw UncheckedException.throwAsUncheckedException(t);
                } finally {
                    currentState.get().remove(resourceLockState);
                }
            }
        }
    }

    @Override
    public ResourceLockState getCurrent() {
        List<ResourceLockState> current = currentState.get();
        if (!current.isEmpty()) {
            int numStates = current.size();
            return current.get(numStates - 1);
        } else {
            return null;
        }
    }

    private void maybeNotifyStateChange(DefaultResourceLockState resourceLockState) {
        Collection<ResourceLock> unlockedResources = resourceLockState.getUnlockedResources();
        if (!unlockedResources.isEmpty()) {
            notifyStateChange();
            for (ResourceLock resource : unlockedResources) {
                for (Action<ResourceLock> releaseHandler : releaseHandlers) {
                    releaseHandler.execute(resource);
                }
            }
        }
    }

    @Override
    public void notifyStateChange() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private static class DefaultResourceLockState implements ResourceLockState {
        private Set<ResourceLock> lockedResources;
        private Set<ResourceLock> unlockedResources;
        boolean rollback;

        @Override
        public void registerLocked(ResourceLock resourceLock) {
            if (!rollback && (unlockedResources == null || !unlockedResources.remove(resourceLock))) {
                if (lockedResources == null) {
                    lockedResources = Sets.newHashSet();
                }
                lockedResources.add(resourceLock);
            }
        }

        @Override
        public void registerUnlocked(ResourceLock resourceLock) {
            if (!rollback && (lockedResources == null || !lockedResources.remove(resourceLock))) {
                if (unlockedResources == null) {
                    unlockedResources = Sets.newHashSet();
                }
                unlockedResources.add(resourceLock);
            }
        }

        Collection<ResourceLock> getUnlockedResources() {
            return unlockedResources == null ? Collections.<ResourceLock>emptyList() : unlockedResources;
        }

        @Override
        public void releaseLocks() {
            if (lockedResources != null) {
                rollback = true;
                try {
                    for (ResourceLock resourceLock : lockedResources) {
                        resourceLock.unlock();
                    }
                    lockedResources.clear();
                } finally {
                    rollback = false;
                }
            }
        }
    }

    /**
     * Attempts an atomic, blocking lock on the provided resource locks.
     */
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> lock(Iterable<? extends ResourceLock> resourceLocks) {
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
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> tryLock(Iterable<? extends ResourceLock> resourceLocks) {
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
    public static Transformer<ResourceLockState.Disposition, ResourceLockState> unlock(Iterable<? extends ResourceLock> resourceLocks) {
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
