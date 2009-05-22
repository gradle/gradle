package org.gradle.api.changedetection.state;

import org.gradle.api.changedetection.digest.DigestStringUtil;
import org.gradle.api.changedetection.digest.DigesterFactory;
import org.gradle.api.GradleException;
import org.gradle.api.io.IoFactory;
import org.gradle.util.GFileUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.text.StrBuilder;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * @author Tom Eyckmans
 */
class StateFileUtil {

    private final File projectRootDirectory;
    private final String absoluteProjectRootPath;

    private final File directoryToProcess;
    private final String absoluteDirectoryToProcessPath;
    private final DigesterFactory shaDigesterFactory;

    private final File directoryStateDir;
    private final File oldDirectoryStateDir;
    private final File newDirectoryStateDir;

    private final IoFactory ioFactory;

    StateFileUtil(File projectRootDirectory, File directoryToProcess, File dotGradleStatesDirectory, DigesterFactory shaDigesterFactory, IoFactory ioFactory) {
        this.projectRootDirectory = projectRootDirectory;
        this.absoluteProjectRootPath = projectRootDirectory.getAbsolutePath();
        this.ioFactory = ioFactory;

        this.directoryToProcess = directoryToProcess;
        this.absoluteDirectoryToProcessPath = directoryToProcess.getAbsolutePath();
        this.shaDigesterFactory = shaDigesterFactory;

        final String relativePathToProjectRoot = getRelativePathToProjectRoot(directoryToProcess);
        final String directoryStateId = getStringDigest(relativePathToProjectRoot);

        directoryStateDir = new File(dotGradleStatesDirectory, directoryStateId);
        oldDirectoryStateDir = new File(directoryStateDir, "old");
        newDirectoryStateDir = new File(directoryStateDir, "new");

        if ( newDirectoryStateDir.exists() ) {
            try {
                FileUtils.deleteDirectory(newDirectoryStateDir);
            }
            catch ( IOException e ) {
                throw new GradleException("failed to clear new state directory " + newDirectoryStateDir.getAbsolutePath(), e);
            }
        }

        if ( !GFileUtils.createDirectoriesWhenNotExistent(oldDirectoryStateDir, newDirectoryStateDir) ) {
            throw new GradleException("failed to create one or more of the state directories [" +
                    oldDirectoryStateDir.getAbsolutePath() + ", " +
                    newDirectoryStateDir.getAbsolutePath() + "]" );
        }
    }

    public File getDirectoryToProcess() {
        return directoryToProcess;
    }

    public String getAbsoluteDirectoryToProcessPath() {
        return absoluteDirectoryToProcessPath;
    }

    public File getDirectoryStateDir() {
        return directoryStateDir;
    }

    public File getOldDirectoryStateDir() {
        return oldDirectoryStateDir;
    }

    public File getNewDirectoryStateDir() {
        return newDirectoryStateDir;
    }

    public String getRelativePathToProjectRoot(final File file) {
        return file.getAbsolutePath().replaceAll(absoluteProjectRootPath, "");
    }

    public String getRelativePathToDirectoryToProcess(final File file) {
        return "." + file.getAbsolutePath().replaceAll(absoluteDirectoryToProcessPath, "");
    }

    public String getStringDigest(final String relativePath) {
        final MessageDigest digester = shaDigesterFactory.createDigester();

        digester.update(relativePath.getBytes());

        return DigestStringUtil.digestToHexString(digester.digest());
    }

    public File getDirsListFile(final int levelIndex) {
        return new File(newDirectoryStateDir, getDirsListFilename(levelIndex));
    }

    public String getDirsListFilename(final int levelIndex) {
        return new StrBuilder().append("dirs.").append(levelIndex).append(".list").toString();
    }

    public File getOldDirsStateFile(final String stateFilename) {
        return new File(oldDirectoryStateDir, stateFilename);
    }

    public String getDirsStateFilename(final int levelIndex) {
        return new StrBuilder().append("dirs.").append(levelIndex).append(".state").toString();
    }

    public File getNewDirsStateFile(final String stateFilename) {
        return new File(newDirectoryStateDir, stateFilename);
    }

    public File getNewDirStateFile(DirectoryState directoryState) {
        return new File(newDirectoryStateDir, getDirStateFilename(directoryState.getRelativePathDigest()));
    }

    public String getDirStateFilename(String relativePathDigest) {
        return new StrBuilder().append("dir.").append(relativePathDigest).append(".state").toString();
    }

    public File getDirsStateFileKeyToFile(String directoriesStateFileItemKey) {
        return new File(directoryToProcess, directoriesStateFileItemKey);
    }

    public String getDirsStateFileKeyToDirStateFile(String directoriesStateFileItemKey) {
        return getDirStateFilename(getStringDigest(directoriesStateFileItemKey));
    }

    public StateFileReader getStateFileReader(final File stateFile) {
        return new StateFileReader(ioFactory, stateFile);
    }

    public StateFileWriter getStateFileWriter(final File stateFile) {
        return new StateFileWriter(ioFactory, stateFile);
    }
}
