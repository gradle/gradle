package org.gradle.api.changedetection.state;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
abstract class AbstractStateComparator implements StateComparator {

    boolean compareLevel(DefaultDirectoryStateChangeDetecter directoryStateChangeDetecter, int level) throws IOException {
        final StateFileComparator stateFileComparator = new StateFileComparator(directoryStateChangeDetecter.getStateFileUtil(), level);
        final DirectoriesStateFileChangeListener directoriesStateFileChangeListener = new DirectoriesStateFileChangeListener(directoryStateChangeDetecter);

        return stateFileComparator.compareStateFiles(directoriesStateFileChangeListener);
    }
}
