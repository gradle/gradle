package org.gradle.api.changedetection.digest;

import java.io.File;
import java.security.MessageDigest;

/**
 * Utility class to calculate the digest of a file or directory based on a requested strategy.
 * 
 * @author Tom Eyckmans
 */
public interface DigesterUtil {
    /**
     * Updates the digester for a file based an the requested strategy.
     *
     * @param digester The digester to update.
     * @param file The file that needs it's digest calculated.
     */
    void digestFile(MessageDigest digester, File file);

    /**
     * Updated the digester for a directory based on the requested strategy.
     *  
     * @param digester The digester to update.
     * @param directory The directory that needs it's digest calculated.
     * @param directorySize The size of the directory to use for digest calculation.
     */
    void digestDirectory(MessageDigest digester, File directory, long directorySize);
}
