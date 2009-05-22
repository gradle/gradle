package org.gradle.api.changedetection.state;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
class AllChangesStateComparator extends AbstractStateComparator {

    public boolean compareState(DefaultDirectoryStateChangeDetecter directoryStateChangeDetecter, int level) throws IOException {
        return compareLevel(directoryStateChangeDetecter, level);
    }
}
