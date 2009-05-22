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
     *
     * @param createdItem
     */
    public void itemCreated(StateFileItem createdItem) {
        stateFileChangeListenerUtil.produceCreatedItemEvent(stateFileItemToFile(createdItem), createdItem);
    }

    /**
     * File deleted
     *
     * @param deletedItem
     */
    public void itemDeleted(StateFileItem deletedItem) {
        stateFileChangeListenerUtil.produceDeletedItemEvent(stateFileItemToFile(deletedItem), deletedItem);
    }

    /**
     * File changed
     *
     * @param oldState
     * @param newState
     */
    public void itemChanged(StateFileItem oldState, StateFileItem newState) {
        stateFileChangeListenerUtil.produceChangedItemEvent(stateFileItemToFile(oldState), oldState, newState);
    }

    File stateFileItemToFile(StateFileItem stateFileItem) {
        return new File(directory, stateFileItem.getKey());
    }
}
