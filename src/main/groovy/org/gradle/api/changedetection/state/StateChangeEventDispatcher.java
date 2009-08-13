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

import org.gradle.util.queues.AbstractBlockingQueueItemConsumer;
import org.gradle.api.changedetection.ChangeProcessor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.io.File;

/**
 * @author Tom Eyckmans
 */
class StateChangeEventDispatcher extends AbstractBlockingQueueItemConsumer<StateChangeEvent> {

    private final ChangeProcessor changeProcessor;

    StateChangeEventDispatcher(BlockingQueue<StateChangeEvent> toConsumeQueue, long pollTimeout, TimeUnit pollTimeoutTimeUnit, ChangeProcessor changeProcessor) {
        super(toConsumeQueue, pollTimeout, pollTimeoutTimeUnit);
        this.changeProcessor = changeProcessor;
    }

    protected boolean consume(StateChangeEvent queueItem) {
        final StateFileItem oldState = queueItem.getOldState();
        final StateFileItem newState = queueItem.getNewState();
        final File fileOrDirectory = queueItem.getFileOrDirectory();

        if ( oldState != null && newState != null ) {
            changeProcessor.changedFile(fileOrDirectory);
        }
        else {
            if ( oldState == null )
                changeProcessor.createdFile(fileOrDirectory);
            else
                changeProcessor.deletedFile(fileOrDirectory);
        }

        return false;
    }
}
