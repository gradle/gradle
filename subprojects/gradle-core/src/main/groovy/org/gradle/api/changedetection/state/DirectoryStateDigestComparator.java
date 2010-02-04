/*
 * Copyright 2010 the original author or authors.
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

import java.io.File;

/**
 * @author Tom Eyckmans
 */
class DirectoryStateDigestComparator implements Runnable {
    private final StateFileItem newState;
    private final File directory;
    private final StateFileUtil stateFileUtil;

    private volatile Throwable failureCause;
    private final StateFileChangeListenerUtil stateFileChangeListenerUtil;

    DirectoryStateDigestComparator(StateFileItem newState, StateFileUtil stateFileUtil,
                                   StateFileChangeListenerUtil stateFileChangeListenerUtil) {
        this.newState = newState;
        this.stateFileChangeListenerUtil = stateFileChangeListenerUtil;
        this.directory = stateFileUtil.getDirsStateFileKeyToFile(newState.getKey());
        this.stateFileUtil = stateFileUtil;
    }

    public void run() {
        final StateFileComparator directoryStateFileComparator = new StateFileComparator(stateFileUtil,
                stateFileUtil.getDirsStateFileKeyToDirStateFile(newState.getKey()));
        try {
            directoryStateFileComparator.compareStateFiles(new DirectoryStateFileChangeListener(
                    stateFileChangeListenerUtil, directory));
        } catch (Throwable t) {
            failureCause = t;
        }
    }

    public Throwable getFailureCause() {
        return failureCause;
    }
}
