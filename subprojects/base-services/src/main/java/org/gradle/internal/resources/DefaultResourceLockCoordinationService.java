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
                            releaseLocks(resourceLockState);
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                            break;
                        case FINISHED:
                            return true;
                        case FAILED:
                            releaseLocks(resourceLockState);
                            return false;
                        default:
                            throw new IllegalArgumentException("Unhandled disposition type: " + disposition.name());
                    }
                } catch (Throwable t) {
                    releaseLocks(resourceLockState);
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

    private void releaseLocks(DefaultResourceLockState stateLock) {
        for (ResourceLock resourceLock : stateLock.getResourceLocks()) {
            resourceLock.unlock();
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
    }


}
