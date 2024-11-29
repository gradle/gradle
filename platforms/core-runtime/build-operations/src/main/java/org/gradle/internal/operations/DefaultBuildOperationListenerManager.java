/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultBuildOperationListenerManager implements BuildOperationListenerManager {

    private final CompositeBuildOperationListener broadcaster = new CompositeBuildOperationListener();
    private final BuildOperationListener shieldedListener = new ProgressShieldingBuildOperationListener(broadcaster);

    @Override
    public void addListener(BuildOperationListener listener) {
        broadcaster.addListener(listener);
    }

    @Override
    public void removeListener(BuildOperationListener listener) {
        broadcaster.removeListener(listener);
    }

    @Override
    public BuildOperationListener getBroadcaster() {
        return shieldedListener;
    }

    /**
     * Prevents sending progress notifications to a given listener outside of start/finished for that operation.
     */
    private static class ProgressShieldingBuildOperationListener implements BuildOperationListener {

        private final Map<OperationIdentifier, Boolean> active = new ConcurrentHashMap<OperationIdentifier, Boolean>();
        private final BuildOperationListener delegate;

        private ProgressShieldingBuildOperationListener(BuildOperationListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            delegate.started(buildOperation, startEvent);
            active.put(buildOperation.getId(), Boolean.TRUE);
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            if (active.containsKey(operationIdentifier)) {
                delegate.progress(operationIdentifier, progressEvent);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            active.remove(buildOperation.getId());
            delegate.finished(buildOperation, finishEvent);
        }
    }

    private static class CompositeBuildOperationListener implements BuildOperationListener {

        // This cannot be CopyOnWriteArrayList because we need to iterate it in reverse,
        // which requires atomically getting an iterator and the size.
        // Moreover, we iterate this list far more often that we mutate,
        // making a (albeit home grown) copy-on-write strategy more appealing.
        private volatile List<BuildOperationListener> listeners = Collections.emptyList();
        private final Lock listenersLock = new ReentrantLock();

        public void addListener(BuildOperationListener listener) {
            listenersLock.lock();
            try {
                List<BuildOperationListener> listeners = new ArrayList<BuildOperationListener>(this.listeners);
                listeners.add(listener);
                this.listeners = listeners;
            } finally {
                listenersLock.unlock();
            }
        }

        public void removeListener(BuildOperationListener listener) {
            listenersLock.lock();
            try {
                List<BuildOperationListener> listeners = new ArrayList<BuildOperationListener>(this.listeners);
                ListIterator<BuildOperationListener> listIterator = listeners.listIterator();
                while (listIterator.hasNext()) {
                    if (listIterator.next().equals(listener)) {
                        listIterator.remove();
                    }
                }
                this.listeners = listeners;
            } finally {
                listenersLock.unlock();
            }
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            List<BuildOperationListener> listeners = this.listeners;
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < listeners.size(); ++i) {
                listeners.get(i).started(buildOperation, startEvent);
            }
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            List<BuildOperationListener> listeners = this.listeners;
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < listeners.size(); ++i) {
                listeners.get(i).progress(operationIdentifier, progressEvent);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            List<BuildOperationListener> listeners = this.listeners;
            for (int i = listeners.size() - 1; i >= 0; --i) {
                listeners.get(i).finished(buildOperation, finishEvent);
            }
        }
    }
}
