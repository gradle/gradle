package org.gradle.api.changedetection.state;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
class DirChangedStateComparator extends AbstractStateComparator {
    

    /**
     * @return Keep comparing?
     */
    public boolean compareState(DefaultDirectoryStateChangeDetecter directoryStateChangeDetecter, int level) throws IOException {

        compareLevel(directoryStateChangeDetecter, level);

        return false;// only compare the top level
    }
}
