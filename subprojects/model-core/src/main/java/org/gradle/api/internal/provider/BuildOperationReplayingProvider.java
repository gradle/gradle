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

package org.gradle.api.internal.provider;

import org.gradle.api.Transformer;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * TBD
 */
public class BuildOperationReplayingProvider<T> extends DefaultProvider<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildOperationReplayingProvider.class);

    private final BuildOperationListenerManager buildOperationListenerManager;

    public BuildOperationReplayingProvider(Callable<? extends T> value, BuildOperationListenerManager buildOperationListenerManager) {
        super(value);
        this.buildOperationListenerManager = buildOperationListenerManager;
    }

    @Override
    public <S> ProviderInternal<S> map(Transformer<? extends S, ? super T> transformer) {
        return new MappingProvider<S, T>(null, this, transformer) {
            // TODO: what if gets mapped again?
            @Override
            protected Value<S> calculateOwnValue(ValueConsumer consumer) {
                Listener listener = new Listener();
                try {
                    buildOperationListenerManager.addListener(listener);
                    return super.calculateOwnValue(consumer);
                } finally {
                    buildOperationListenerManager.removeListener(listener);
                    for (Object event : listener.events) {
                        LOGGER.warn("Replaying event (mapped): {}", event);
                    }
                }
            }
        };
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        Listener listener = new Listener();
        try {
            buildOperationListenerManager.addListener(listener);
            return super.calculateOwnValue(consumer);
        } finally {
            buildOperationListenerManager.removeListener(listener);
            for (Object event : listener.events) {
                LOGGER.warn("Replaying event: {}", event);
            }
        }
    }

    private static class Listener implements BuildOperationListener {

        private final List<Object> events = new ArrayList<>();

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            events.add(Arrays.asList(buildOperation, startEvent));
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            events.add(Arrays.asList(operationIdentifier, progressEvent));
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            events.add(Arrays.asList(buildOperation, finishEvent));
        }
    }


}
