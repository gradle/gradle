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

    // This cannot be CopyOnWriteArrayList because we need to iterate it in reverse,
    // which requires atomically getting an iterator and the size.
    // Moreover, we iterate this list far more often that we mutate,
    // making a (albeit home grown) copy-on-write strategy more appealing.
    private List<ProgressShieldingBuildOperationListener> listeners = Collections.emptyList();
    private final Lock listenersLock = new ReentrantLock();

    private final BuildOperationListener broadcaster = new BuildOperationListener() {
        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            List<? extends BuildOperationListener> listeners = DefaultBuildOperationListenerManager.this.listeners;
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < listeners.size(); ++i) {
                listeners.get(i).started(buildOperation, startEvent);
            }
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            List<? extends BuildOperationListener> listeners = DefaultBuildOperationListenerManager.this.listeners;
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < listeners.size(); ++i) {
                listeners.get(i).progress(operationIdentifier, progressEvent);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            List<? extends BuildOperationListener> listeners = DefaultBuildOperationListenerManager.this.listeners;
            for (int i = listeners.size() - 1; i >= 0; --i) {
                listeners.get(i).finished(buildOperation, finishEvent);
            }
        }
    };

    @Override
    public void addListener(BuildOperationListener listener) {
        listenersLock.lock();
        try {
            List<ProgressShieldingBuildOperationListener> listeners = new ArrayList<ProgressShieldingBuildOperationListener>(this.listeners);
            listeners.add(new ProgressShieldingBuildOperationListener(listener));
            this.listeners = listeners;
        } finally {
            listenersLock.unlock();
        }
    }

    @Override
    public void removeListener(BuildOperationListener listener) {
        listenersLock.lock();
        try {
            List<ProgressShieldingBuildOperationListener> listeners = new ArrayList<ProgressShieldingBuildOperationListener>(this.listeners);
            ListIterator<ProgressShieldingBuildOperationListener> listIterator = listeners.listIterator();
            while (listIterator.hasNext()) {
                if (listIterator.next().delegate.equals(listener)) {
                    listIterator.remove();
                }
            }
            this.listeners = listeners;
        } finally {
            listenersLock.unlock();
        }
    }

    @Override
    public BuildOperationListener getBroadcaster() {
        return broadcaster;
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
            active.put(buildOperation.getId(), Boolean.TRUE);
            delegate.started(buildOperation, startEvent);
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
}
