package org.gradle.api.changedetection.state;

import java.io.File;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
class StateFileComparator {

    private final StateFileUtil stateFileUtil;
    private final File oldStateFile;
    private final File newStateFile;

    StateFileComparator(final StateFileUtil stateFileUtil, final int levelIndex) {
        this(stateFileUtil, stateFileUtil.getDirsStateFilename(levelIndex));
    }

    StateFileComparator(final StateFileUtil stateFileUtil, final String stateFilename) {
        this.stateFileUtil = stateFileUtil;
        this.oldStateFile = stateFileUtil.getOldDirsStateFile(stateFilename);
        this.newStateFile = stateFileUtil.getNewDirsStateFile(stateFilename);
    }

    public boolean compareStateFiles(StateFileChangeListener stateFileChangeListener) throws IOException {
        boolean stateFileChanged = false;

        StateFileReader oldDirectoriesLevelStateReader = null;
        StateFileReader newDirectoriesLevelStateReader = null;
        boolean readNextOldItem = true;
        StateFileItem oldItem = null;
        boolean readNextNewItem = true;
        StateFileItem newItem = null;
        try {
            oldDirectoriesLevelStateReader = stateFileUtil.getStateFileReader(oldStateFile);
            newDirectoriesLevelStateReader = stateFileUtil.getStateFileReader(newStateFile);

            while ( readNextNewItem || readNextOldItem ) { // in general there is higher likelyhood in projects for more new items then old ones

                if ( readNextOldItem )
                    oldItem = oldDirectoriesLevelStateReader.readStateFileItem();
                if ( readNextNewItem )
                    newItem = newDirectoriesLevelStateReader.readStateFileItem();

                if ( oldItem == null ) { // no more old items
                    if ( newItem == null ) { // no more new items
                        // current directory state file comparison is done
                        readNextOldItem = false;
                        readNextNewItem = false;
                    }
                    else { // there are new items
                        stateFileChanged = true;

                        // item was created
                        stateFileChangeListener.itemCreated(newItem);

                        readNextOldItem = false;
                        readNextNewItem = true;
                    }
                }
                else { // there are old items
                    if ( newItem == null ) { // no more new items
                        stateFileChanged = true;

                        // item was deleted
                        stateFileChangeListener.itemDeleted(oldItem);

                        readNextOldItem = true;
                        readNextNewItem = false;
                    }
                    else { // there is a new item
                        final String oldItemKey = oldItem.getKey();
                        final String newItemKey = newItem.getKey();
                        final int keyComparisonResult = oldItemKey.compareTo(newItemKey);

                        if ( keyComparisonResult == 0 ) { // same item?
                            final String oldItemDigest = oldItem.getDigest();
                            final String newItemDigest = newItem.getDigest();

                            if ( !oldItemDigest.equals(newItemDigest) ) { // item changed?
                                stateFileChanged = true;

                                // item has changed
                                stateFileChangeListener.itemChanged(oldItem, newItem);
                            }
                            // else item has not changed

                            readNextOldItem = true;
                            readNextNewItem = true;
                        }
                        else if ( keyComparisonResult < 0 ) { // old key is before the new key (alphabetically)
                            stateFileChanged = true;

                            // old item was deleted
                            stateFileChangeListener.itemDeleted(oldItem);

                            readNextOldItem = true;
                            readNextNewItem = false;
                        }
                        else if ( keyComparisonResult > 0 ) { // old key is after the new key (alphabetically)
                            stateFileChanged = true;

                            // new item has been created
                            stateFileChangeListener.itemCreated(newItem);

                            readNextOldItem = false;
                            readNextNewItem = true;
                        }
                    }
                }
            }
        }
        finally {
            if ( oldDirectoriesLevelStateReader != null )
                oldDirectoriesLevelStateReader.lastStateFileItemRead();
            if ( newDirectoriesLevelStateReader != null )
                newDirectoriesLevelStateReader.lastStateFileItemRead();
        }

        return stateFileChanged;
    }


}
