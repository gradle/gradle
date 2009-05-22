package org.gradle.api.changedetection.state;

import org.gradle.api.changedetection.state.StateChangeEvent;
import org.gradle.util.queues.BlockingQueueItemProducer;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
class DirectoryStateDigestComparator implements Runnable{
    private final StateFileItem newState;
    private final File directory;
    private final StateFileUtil stateFileUtil;

    private volatile Throwable failureCause = null;
    private final StateFileChangeListenerUtil stateFileChangeListenerUtil;

    DirectoryStateDigestComparator(StateFileItem newState, StateFileUtil stateFileUtil, StateFileChangeListenerUtil stateFileChangeListenerUtil) {
        this.newState = newState;
        this.stateFileChangeListenerUtil = stateFileChangeListenerUtil;
        this.directory = stateFileUtil.getDirsStateFileKeyToFile(newState.getKey());
        this.stateFileUtil = stateFileUtil;
    }

    public void run() {
        final StateFileComparator directoryStateFileComparator = new StateFileComparator(stateFileUtil, stateFileUtil.getDirsStateFileKeyToDirStateFile(newState.getKey()));
        try {
            directoryStateFileComparator.compareStateFiles(new DirectoryStateFileChangeListener(stateFileChangeListenerUtil, directory));
        }
        catch ( Throwable t ) {
            failureCause = t;
        }
    }

    public Throwable getFailureCause() {
        return failureCause;
    }
}
