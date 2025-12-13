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

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultBuildOperationListenerManager implements BuildOperationListenerManager {

    // Imitation of CopyOnWriteArrayList, which supports safe iteration in reverse
    private final AtomicReference<ImmutableList<ProgressShieldingBuildOperationListener>> listeners = new AtomicReference<>(ImmutableList.of());

    private final BuildOperationListener broadcaster = new BuildOperationListener() {
        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            List<? extends BuildOperationListener> listeners = getListeners();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < listeners.size(); ++i) {
                listeners.get(i).started(buildOperation, startEvent);
            }
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            List<? extends BuildOperationListener> listeners = getListeners();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < listeners.size(); ++i) {
                listeners.get(i).progress(operationIdentifier, progressEvent);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            List<? extends BuildOperationListener> listeners = getListeners();
            for (int i = listeners.size() - 1; i >= 0; --i) {
                listeners.get(i).finished(buildOperation, finishEvent);
            }
        }
    };

    @SuppressWarnings("NullAway") // TODO(https://github.com/uber/NullAway/issues/681) Can't infer that AtomicReference holds non-nullable type
    private ImmutableList<ProgressShieldingBuildOperationListener> getListeners() {
        return DefaultBuildOperationListenerManager.this.listeners.get();
    }

    @Override
    public void addListener(BuildOperationListener listener) {
        listeners.updateAndGet(current ->
            ImmutableList.<ProgressShieldingBuildOperationListener>builderWithExpectedSize(current.size() + 1)
                .addAll(current)
                .add(new ProgressShieldingBuildOperationListener(listener))
                .build());
    }

    @Override
    public void removeListener(BuildOperationListener listener) {
        listeners.updateAndGet(current ->
            current.stream()
                .filter(l -> !l.delegate.equals(listener))
                .collect(ImmutableList.toImmutableList()));
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
