package org.gradle.api.changedetection.state;

import org.gradle.api.changedetection.state.StateFileItem;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
class StateChangeEvent {
    private final File fileOrDirectory;
    private final StateFileItem oldState;
    private final StateFileItem newState;

    StateChangeEvent(final File fileOrDirectory, StateFileItem oldState, StateFileItem newState) {
        if ( fileOrDirectory == null ) throw new IllegalArgumentException("fileOrDirectory is null!");
        if ( oldState == null && newState == null ) throw new IllegalArgumentException("old and new state are null!");
        this.fileOrDirectory = fileOrDirectory;
        this.oldState = oldState;
        this.newState = newState;
    }

    public File getFileOrDirectory() {
        return fileOrDirectory;
    }

    public StateFileItem getOldState() {
        return oldState;
    }

    public StateFileItem getNewState() {
        return newState;
    }
}
