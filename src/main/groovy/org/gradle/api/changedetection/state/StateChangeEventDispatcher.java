package org.gradle.api.changedetection.state;

import org.gradle.util.queues.AbstractBlockingQueueItemConsumer;
import org.gradle.api.changedetection.state.StateFileItem;
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
