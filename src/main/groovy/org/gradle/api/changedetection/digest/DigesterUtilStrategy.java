package org.gradle.api.changedetection.digest;

import java.security.MessageDigest;
import java.io.File;

/**
 * Interface for actual digester update algorithms.
 *
 * @author Tom Eyckmans
 */
public interface DigesterUtilStrategy {
    /**
     * Called by a DigesterUtil.
     *
     * The DigesterUtil needs to make sure that the following holds true before calling this method:
     * - digester is not null
     * - file is not null
     * - the file exists
     * - the file is a file
     *
     * @param digester The digester to update.
     * @param file The file that needs it's digest calculated.
     */
    void digestFile(MessageDigest digester, File file);

    /**
     * Called by a DigesterUtil.
     *
     * The DigesterUtil needs to make sure that the following holds true before calling this method:
     * - digester is not null
     * - directory is not null
     * - the directory exists
     * - the directory is a directory
     *
     * @param digester The digester to update.
     * @param directory The directory that needs it's digest calculated.
     * @param directorySize The directory size that needs to be used during digest calculation.
     */
    void digestDirectory(MessageDigest digester, File directory, long directorySize);
}
