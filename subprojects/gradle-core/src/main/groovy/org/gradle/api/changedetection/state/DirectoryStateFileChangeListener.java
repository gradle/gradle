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

import java.io.File;

/**
 * @author Tom Eyckmans
 */
class DirectoryStateFileChangeListener implements StateFileChangeListener {
    private final StateFileChangeListenerUtil stateFileChangeListenerUtil;
    private final File directory;

    DirectoryStateFileChangeListener(final StateFileChangeListenerUtil stateFileChangeListenerUtil, File directory) {
        this.stateFileChangeListenerUtil = stateFileChangeListenerUtil;
        this.directory = directory;
    }

    /**
     * File created
     */
    public void itemCreated(StateFileItem createdItem) {
        stateFileChangeListenerUtil.produceCreatedItemEvent(stateFileItemToFile(createdItem), createdItem);
    }

    /**
     * File deleted
     */
    public void itemDeleted(StateFileItem deletedItem) {
        stateFileChangeListenerUtil.produceDeletedItemEvent(stateFileItemToFile(deletedItem), deletedItem);
    }

    /**
     * File changed
     */
    public void itemChanged(StateFileItem oldState, StateFileItem newState) {
        stateFileChangeListenerUtil.produceChangedItemEvent(stateFileItemToFile(oldState), oldState, newState);
    }

    File stateFileItemToFile(StateFileItem stateFileItem) {
        return new File(directory, stateFileItem.getKey());
    }
}
