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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.cache.PersistentStateCache;
import org.gradle.cache.internal.FileIntegrityViolationSuppressingPersistentStateCacheDecorator;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.OnDemandFileAccess;
import org.gradle.cache.internal.SimpleStateCache;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.internal.remote.Address;
import org.gradle.launcher.daemon.context.DaemonContext;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State;
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Canceled;
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle;

/**
 * Access to daemon registry files. Useful also for testing.
 */
public class PersistentDaemonRegistry implements DaemonRegistry {
    private final PersistentStateCache<DaemonRegistryContent> cache;
    private final Lock lock = new ReentrantLock();
    private final File registryFile;

    private static final Logger LOGGER = Logging.getLogger(PersistentDaemonRegistry.class);

    public PersistentDaemonRegistry(File registryFile, FileLockManager fileLockManager, Chmod chmod) {
        this.registryFile = registryFile;
        cache = new FileIntegrityViolationSuppressingPersistentStateCacheDecorator<DaemonRegistryContent>(
                new SimpleStateCache<DaemonRegistryContent>(
                        registryFile,
                        new OnDemandFileAccess(
                                registryFile,
                                "daemon addresses registry",
                                fileLockManager),
                        DaemonRegistryContent.SERIALIZER,
                        chmod
                ));
    }

    public List<DaemonInfo> getAll() {
        lock.lock();
        try {
            DaemonRegistryContent content = cache.get();
            if (content == null) {
                //when no daemon process has started yet
                return new LinkedList<DaemonInfo>();
            }
            return content.getInfos();
        } finally {
            lock.unlock();
        }
    }

    public List<DaemonInfo> getIdle() {
        return getDaemonsMatching(new Spec<DaemonInfo>() {
            @Override
            public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
                return daemonInfo.getState() == Idle;
            }
        });
    }

    public List<DaemonInfo> getNotIdle() {
        return getDaemonsMatching(new Spec<DaemonInfo>() {
            @Override
            public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
                return daemonInfo.getState() != Idle;
            }
        });
    }

    public List<DaemonInfo> getCanceled() {
        return getDaemonsMatching(new Spec<DaemonInfo>() {
            @Override
            public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
                return daemonInfo.getState() == Canceled;
            }
        });
    }

    private List<DaemonInfo> getDaemonsMatching(Spec<DaemonInfo> spec) {
        lock.lock();
        try {
            List<DaemonInfo> out = new LinkedList<DaemonInfo>();
            List<DaemonInfo> all = getAll();
            for (DaemonInfo d : all) {
                if (spec.isSatisfiedBy(d)) {
                    out.add(d);
                }
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    public void remove(final Address address) {
        lock.lock();
        LOGGER.debug("Removing daemon address: {}", address);
        try {
            cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
                public DaemonRegistryContent update(DaemonRegistryContent oldValue) {
                    if (oldValue == null) {
                        return oldValue;
                    }
                    oldValue.removeInfo(address);
                    return oldValue;
                }
            });
        } finally {
            lock.unlock();
        }
    }

    public void markState(final Address address, final State state) {
        lock.lock();
        LOGGER.debug("Marking busy by address: {}", address);
        try {
            cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
                public DaemonRegistryContent update(DaemonRegistryContent oldValue) {
                    DaemonInfo daemonInfo = oldValue != null ? oldValue.getInfo(address) : null;
                    if (daemonInfo != null) {
                        daemonInfo.setState(state);
                    }
                    // Else, has been removed by something else - ignore
                    return oldValue;
                }});
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void storeStopEvent(final DaemonStopEvent stopEvent) {
        lock.lock();
        LOGGER.debug("Storing daemon stop event with timestamp {}", stopEvent.getTimestamp().getTime());
        try {
            cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
                public DaemonRegistryContent update(DaemonRegistryContent content) {
                    if (content == null) { // registry doesn't exist yet
                        content = new DaemonRegistryContent();
                    }
                    content.addStopEvent(stopEvent);
                    return content;
                }
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<DaemonStopEvent> getStopEvents() {
        lock.lock();
        LOGGER.debug("Getting daemon stop events");
        try {
            DaemonRegistryContent content = cache.get();
            if (content == null) { // no daemon process has started yet
                return new LinkedList<DaemonStopEvent>();
            }
            return content.getStopEvents();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeStopEvents(final Collection<DaemonStopEvent> events) {
        lock.lock();
        LOGGER.info("Removing {} daemon stop events from registry", events.size());
        try {
            cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
                public DaemonRegistryContent update(DaemonRegistryContent content) {
                    if (content != null) { // no daemon process has started yet
                        content.removeStopEvents(events);
                    }
                    return content;
                }
            });
        } finally {
            lock.unlock();
        }
    }

    public void store(final DaemonInfo info) {
        final Address address = info.getAddress();
        final DaemonContext daemonContext = info.getContext();
        final byte[] token = info.getToken();
        final State state = info.getState();

        lock.lock();
        LOGGER.debug("Storing daemon address: {}, context: {}", address, daemonContext);
        try {
            cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
                public DaemonRegistryContent update(DaemonRegistryContent oldValue) {
                    if (oldValue == null) {
                        //it means the registry didn't exist yet
                        oldValue = new DaemonRegistryContent();
                    }
                    DaemonInfo daemonInfo = new DaemonInfo(address, daemonContext, token, state);
                    oldValue.setStatus(address, daemonInfo);
                    return oldValue;
                }
            });
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        return String.format("PersistentDaemonRegistry[file=%s]", registryFile);
    }
}
