package org.gradle.api.changedetection.state;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class StateChangeEventFactory {
    public StateChangeEvent createStateChangeEvent(final File fileOrDirectory, StateFileItem oldState, StateFileItem newState) {
        return new StateChangeEvent(fileOrDirectory, oldState, newState);
    }
}
