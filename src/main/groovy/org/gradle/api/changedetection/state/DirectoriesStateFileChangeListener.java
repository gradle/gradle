/*
 * Copyright 2009 the original author or authors.
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

/**
 * @author Tom Eyckmans
 */
class DirectoriesStateFileChangeListener implements StateFileChangeListener {
    private final StateFileChangeListenerUtil stateFileChangeListenerUtil;
    private final StateFileUtil stateFileUtil;
    private final DefaultDirectoryStateChangeDetecter directoryStateChangeDetecter;

    DirectoriesStateFileChangeListener(final DefaultDirectoryStateChangeDetecter directoryStateChangeDetecter) {
        this.stateFileChangeListenerUtil = directoryStateChangeDetecter.getStateFileChangeListenerUtil();
        this.stateFileUtil = directoryStateChangeDetecter.getStateFileUtil();
        this.directoryStateChangeDetecter = directoryStateChangeDetecter;
    }

    /**
     * Directory created
     *
     * @param createdItem
     */
    public void itemCreated(final StateFileItem createdItem) {
        stateFileChangeListenerUtil.produceCreatedItemEvent(stateFileUtil.getDirsStateFileKeyToFile(createdItem.getKey()), createdItem);
    }

    /**
     * Directory deleted.
     *
     * @param deletedItem
     */
    public void itemDeleted(final StateFileItem deletedItem) {
        // directory deleted
        stateFileChangeListenerUtil.produceDeletedItemEvent(stateFileUtil.getDirsStateFileKeyToFile(deletedItem.getKey()), deletedItem);
    }

    public void itemChanged(final StateFileItem oldState, final StateFileItem newState) {
        // directory changed

        // check files in directory
        directoryStateChangeDetecter.submitDirectoryStateDigestComparator(
                new DirectoryStateDigestComparator(
                newState,
                stateFileUtil, 
                stateFileChangeListenerUtil));
    }
}
