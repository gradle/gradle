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
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.MutableReference;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultResourceLockCoordinationService implements ResourceLockCoordinationService, Closeable {
    private final Object lock = new Object();
    private final Set<Action<ResourceLock>> releaseHandlers = new LinkedHashSet<Action<ResourceLock>>();
    private Thread currentOwner;
    private DefaultResourceLockState currentState;

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
        withStateLock(new InternalTransformer<ResourceLockState.Disposition, ResourceLockState>() {
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
        withStateLock(new InternalTransformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                result.set(action.get());
                return ResourceLockState.Disposition.FINISHED;
            }
        });
        return result.get();
    }

    @Override
    public boolean withStateLock(InternalTransformer<ResourceLockState.Disposition, ResourceLockState> stateLockAction) {
        synchronized (lock) {
            DefaultResourceLockState resourceLockState = new DefaultResourceLockState();
            DefaultResourceLockState previous = startOperation(resourceLockState);
            try {
                while (true) {
                    ResourceLockState.Disposition disposition;
                    disposition = stateLockAction.transform(resourceLockState);
                    switch (disposition) {
                        case RETRY:
                            resourceLockState.releaseLocks();
                            maybeNotifyStateChange(resourceLockState);
                            resourceLockState.reset();
                            finishOperation(previous);
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                            startOperation(resourceLockState);
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
                }
            } catch (Throwable t) {
                resourceLockState.releaseLocks();
                throw UncheckedException.throwAsUncheckedException(t);
            } finally {
                finishOperation(previous);
            }
        }
    }

    private DefaultResourceLockState startOperation(DefaultResourceLockState newState) {
        if (currentOwner == null) {
            currentOwner = Thread.currentThread();
        } else if (currentOwner != Thread.currentThread()) {
            throw new IllegalStateException("Another thread holds the state lock.");
        }
        DefaultResourceLockState previousState = currentState;
        this.currentState = newState;
        return previousState;
    }

    private void finishOperation(@Nullable DefaultResourceLockState previous) {
        if (currentOwner != Thread.currentThread()) {
            throw new IllegalStateException("Another thread holds the state lock.");
        }
        currentState = previous;
        if (currentState == null) {
            currentOwner = null;
        }
    }

    @Override
    public ResourceLockState getCurrent() {
        synchronized (lock) {
            if (currentOwner != Thread.currentThread()) {
                return null;
            } else {
                return currentState;
            }
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

        public void reset() {
            if (lockedResources != null) {
                lockedResources.clear();
            }
            if (unlockedResources != null) {
                unlockedResources.clear();
            }
            rollback = false;
        }
    }

    /**
     * Attempts an atomic, blocking lock on the provided resource locks.
     */
    public static InternalTransformer<ResourceLockState.Disposition, ResourceLockState> lock(Iterable<? extends ResourceLock> resourceLocks) {
        return new AcquireLocks(resourceLocks, true);
    }

    /**
     * Attempts an atomic, blocking lock on the provided resource locks.
     */
    public static InternalTransformer<ResourceLockState.Disposition, ResourceLockState> lock(ResourceLock... resourceLocks) {
        return lock(Arrays.asList(resourceLocks));
    }

    /**
     * Attempts an atomic, non-blocking lock on the provided resource locks.
     */
    public static InternalTransformer<ResourceLockState.Disposition, ResourceLockState> tryLock(Iterable<? extends ResourceLock> resourceLocks) {
        return new AcquireLocks(resourceLocks, false);
    }

    /**
     * Attempts an atomic, non-blocking lock on the provided resource locks.
     */
    public static InternalTransformer<ResourceLockState.Disposition, ResourceLockState> tryLock(ResourceLock... resourceLocks) {
        return tryLock(Arrays.asList(resourceLocks));
    }

    /**
     * Unlocks the provided resource locks.
     */
    public static InternalTransformer<ResourceLockState.Disposition, ResourceLockState> unlock(Iterable<? extends ResourceLock> resourceLocks) {
        return new ReleaseLocks(resourceLocks);
    }

    /**
     * Unlocks the provided resource locks.
     */
    public static InternalTransformer<ResourceLockState.Disposition, ResourceLockState> unlock(ResourceLock... resourceLocks) {
        return unlock(Arrays.asList(resourceLocks));
    }

    private static class AcquireLocks implements InternalTransformer<ResourceLockState.Disposition, ResourceLockState> {
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

    private static class ReleaseLocks implements InternalTransformer<ResourceLockState.Disposition, ResourceLockState> {
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
