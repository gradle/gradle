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
