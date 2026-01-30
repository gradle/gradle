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
import org.gradle.internal.concurrent.MultiProducerSingleConsumerProcessor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultBuildOperationListenerManager implements BuildOperationListenerManager {

    // Imitation of CopyOnWriteArrayList, which supports safe iteration in reverse
    private final AtomicReference<ImmutableList<ProgressShieldingBuildOperationListener>> listeners = new AtomicReference<>(ImmutableList.of());

    private final AsyncCompositeBuildOperationListener broadcaster = new AsyncCompositeBuildOperationListener(listeners);

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

    @Override
    public void stop() {
        broadcaster.stop();
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

    private static class AsyncCompositeBuildOperationListener implements BuildOperationListener {

        private final AtomicReference<ImmutableList<ProgressShieldingBuildOperationListener>> listeners;

        private final MultiProducerSingleConsumerProcessor<Object> queue;

        public AsyncCompositeBuildOperationListener(
            AtomicReference<ImmutableList<ProgressShieldingBuildOperationListener>> listeners
        ) {
            this.listeners = listeners;

            this.queue = new MultiProducerSingleConsumerProcessor<>("build-operation-broadcaster", this::processEvent);
            queue.start();
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            queue.submit(new Started(buildOperation, startEvent));
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            queue.submit(new Progress(operationIdentifier, progressEvent));
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            queue.submit(new Finished(buildOperation, finishEvent));
        }

        @SuppressWarnings("NullAway") // TODO(https://github.com/uber/NullAway/issues/681) Can't infer that AtomicReference holds non-nullable type
        private void processEvent(Object event) {
            if (event instanceof Started) {
                Started started = (Started) event;
                List<? extends BuildOperationListener> currentListeners = listeners.get();
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < currentListeners.size(); ++i) {
                    currentListeners.get(i).started(started.buildOperation, started.startEvent);
                }
            } else if (event instanceof Progress) {
                Progress progress = (Progress) event;
                List<? extends BuildOperationListener> currentListeners = listeners.get();
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < currentListeners.size(); ++i) {
                    currentListeners.get(i).progress(progress.operationIdentifier, progress.progressEvent);
                }
            } else if (event instanceof Finished) {
                Finished finished = (Finished) event;
                List<? extends BuildOperationListener> currentListeners = listeners.get();
                for (int i = currentListeners.size() - 1; i >= 0; --i) {
                    currentListeners.get(i).finished(finished.buildOperation, finished.finishEvent);
                }
            } else {
                throw new IllegalArgumentException("Unsupported event type: " + event.getClass().getName());
            }
        }

        public void stop() {
            queue.stop(null);
        }
    }

    private static class Started {
        final BuildOperationDescriptor buildOperation;
        final OperationStartEvent startEvent;

        public Started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            this.buildOperation = buildOperation;
            this.startEvent = startEvent;
        }
    }

    private static class Progress {
        final OperationIdentifier operationIdentifier;
        final OperationProgressEvent progressEvent;

        public Progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            this.operationIdentifier = operationIdentifier;
            this.progressEvent = progressEvent;
        }
    }

    private static class Finished {
        final BuildOperationDescriptor buildOperation;
        final OperationFinishEvent finishEvent;

        public Finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            this.buildOperation = buildOperation;
            this.finishEvent = finishEvent;
        }
    }
}
