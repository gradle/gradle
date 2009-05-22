package org.gradle.api.changedetection.state;

import org.gradle.api.changedetection.digest.DigesterCache;
import org.gradle.api.changedetection.digest.DigesterUtil;
import org.gradle.api.changedetection.digest.DigestStringUtil;
import org.gradle.api.io.IoFactory;
import org.gradle.util.GFileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.security.MessageDigest;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
class DirectoryStateDigestCalculator implements Runnable {

    private final DirectoryState directoryState;
    private final DigesterCache digesterCache;
    private final DigesterUtil digesterUtil;
    private final DefaultDirectoryStateChangeDetecter directoryStateChangeDetecter;
    private final Map<String, DirectoryState> currentLevelDirectoryStates;
    private final Map<String, DirectoryState> previousLevelDirectoryStates;
    private final IoFactory ioFactory;

    DirectoryStateDigestCalculator(
            DirectoryState directoryState,
            DigesterCache digesterCache,
            DigesterUtil digesterUtil,
            DefaultDirectoryStateChangeDetecter directoryStateChangeDetecter,
            Map<String, DirectoryState> currentLevelDirectoryStates,
            Map<String, DirectoryState> previousLevelDirectoryStates, IoFactory ioFactory) {
        this.directoryState = directoryState;
        this.digesterCache = digesterCache;
        this.digesterUtil = digesterUtil;
        this.directoryStateChangeDetecter = directoryStateChangeDetecter;
        this.currentLevelDirectoryStates = currentLevelDirectoryStates;
        this.previousLevelDirectoryStates = previousLevelDirectoryStates;
        this.ioFactory = ioFactory;
    }

    public void run() {
        StateFileWriter stateFileWriter  = null;
        try {
            final MessageDigest fileDigester = digesterCache.getDigester(Thread.currentThread().getName() + "_file");
            final MessageDigest dirDigester = digesterCache.getDigester(Thread.currentThread().getName() + "_dir");
            final File directory = directoryState.getDirectory();
            final String relativeDirectoryPath = directoryState.getRelativePath();
            final StateFileUtil stateFileUtil = directoryStateChangeDetecter.getStateFileUtil();
            final File stateFile = stateFileUtil.getNewDirsStateFile(stateFileUtil.getDirStateFilename(directoryState.getRelativePathDigest())); 
            stateFileWriter = new StateFileWriter(ioFactory, stateFile);

            long directorySize = 0;

            final List<File> subFiles = GFileUtils.getSubFiles(directory);

            if ( subFiles.size() > 0 ) {
                // Sort alphabetically by filename - this simplifies comparing agains the old state later on.
                Collections.sort(subFiles, new Comparator<File>() {
                    public int compare(final File firstFile, final File secondFile) {
                        return firstFile.getName().compareTo(secondFile.getName());
                    }
                });

                for ( final File subFile : subFiles ) {
                    digesterUtil.digestFile(fileDigester, subFile);

                    final String fileDigest = DigestStringUtil.digestToHexString(fileDigester.digest());

                    stateFileWriter.addDigest(subFile.getName(), fileDigest);

                    directorySize += subFile.length();
                    dirDigester.update(fileDigest.getBytes());
                }

                stateFileWriter.lastFileDigestAdded();
            }

            final List<DirectoryState> subDirectoryStateItems = getNewSubDirectoryStates(relativeDirectoryPath);
            for ( final DirectoryState subDirectoryStateItem : subDirectoryStateItems ) {
                dirDigester.update(subDirectoryStateItem.getDigest().getBytes());
                directorySize += subDirectoryStateItem.getSize();
            }

            digesterUtil.digestDirectory(dirDigester, directory, directorySize);

            final String dirDigest = DigestStringUtil.digestToHexString(dirDigester.digest());

            directoryState.setDigest(dirDigest);
            directoryState.setSize(directorySize);
        }
        catch ( Throwable t ) {
            directoryState.setFailureCause(t);
        }
        finally {
            addNewDirectoryState(directoryState);
            if ( stateFileWriter != null ) {
                stateFileWriter.close();
            }
        }
    }

    public void addNewDirectoryState(DirectoryState directoryState) {
        if ( directoryState == null ) throw new IllegalArgumentException("leveledDirectoryState is null!");

        currentLevelDirectoryStates.put(directoryState.getRelativePath(), directoryState);
    }

    public String getNewDirectoryDigest(String relativeDirectoryPath) {
        if ( StringUtils.isEmpty(relativeDirectoryPath) ) throw new IllegalArgumentException("relativeDirectoryPath is empty!");

        final DirectoryState directoryState = previousLevelDirectoryStates.get(relativeDirectoryPath);
        if ( directoryState == null ) {
            return null;
        }
        else {
            return directoryState.getDigest();
        }
    }

    public List<DirectoryState> getNewSubDirectoryStates(String relativeDirectoryPath) {
        final List<DirectoryState> subDirectoryStates = new ArrayList<DirectoryState>();

        for (final String currentStateItemKey : previousLevelDirectoryStates.keySet()) {

            if (    currentStateItemKey.startsWith(relativeDirectoryPath) ) {
                String belowPath = currentStateItemKey.replaceAll(relativeDirectoryPath, "");
                if ( "".equals(belowPath) || StringUtils.countMatches(belowPath, System.getProperty("file.separator")) == 1 )
                    subDirectoryStates.add(previousLevelDirectoryStates.get(currentStateItemKey));
            }
        }

        return subDirectoryStates;
    }
}
