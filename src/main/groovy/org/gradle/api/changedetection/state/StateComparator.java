package org.gradle.api.changedetection.state;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
interface StateComparator {

    boolean compareState(DefaultDirectoryStateChangeDetecter directoryStateChangeDetecter, int level) throws IOException;

}
