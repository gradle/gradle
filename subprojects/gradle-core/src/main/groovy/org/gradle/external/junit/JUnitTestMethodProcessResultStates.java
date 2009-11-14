package org.gradle.external.junit;

import org.gradle.api.testing.fabric.TestMethodProcessResultState;

/**
 * @author Tom Eyckmans
 */
public enum JUnitTestMethodProcessResultStates implements TestMethodProcessResultState {
    SUCCESS,
    FAILURE,
    ERROR
}
