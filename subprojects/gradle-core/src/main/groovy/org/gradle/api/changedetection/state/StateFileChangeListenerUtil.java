/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.changedetection.state;

import org.gradle.util.queues.BlockingQueueItemProducer;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
class StateFileChangeListenerUtil {

    private final BlockingQueueItemProducer<StateChangeEvent> changeProcessorEventProducer;
    private final StateChangeEventFactory stateChangeEventFactory;

    StateFileChangeListenerUtil(final BlockingQueueItemProducer<StateChangeEvent> changeProcessorEventProducer, final StateChangeEventFactory stateChangeEventFactory) {
        if ( changeProcessorEventProducer == null ) throw new IllegalArgumentException("changeProcessorEventProducer is null!");
        if ( stateChangeEventFactory == null ) throw new IllegalArgumentException("stateChangeEventFactory is null!");

        this.changeProcessorEventProducer = changeProcessorEventProducer;
        this.stateChangeEventFactory = stateChangeEventFactory;
    }

    BlockingQueueItemProducer<StateChangeEvent> getChangeProcessorEventProducer() {
        return changeProcessorEventProducer;
    }

    void produceCreatedItemEvent(final File fileOrDirectory, final StateFileItem newState) {
        changeProcessorEventProducer.produce(stateChangeEventFactory.createStateChangeEvent(fileOrDirectory, null, newState));
    }

    void produceChangedItemEvent(final File fileOrDirectory, final StateFileItem oldState, final StateFileItem newState) {
        changeProcessorEventProducer.produce(stateChangeEventFactory.createStateChangeEvent(fileOrDirectory, oldState, newState));
    }

    void produceDeletedItemEvent(final File fileOrDirectory, final StateFileItem oldState) {
        changeProcessorEventProducer.produce(stateChangeEventFactory.createStateChangeEvent(fileOrDirectory, oldState, null));
    }
}
