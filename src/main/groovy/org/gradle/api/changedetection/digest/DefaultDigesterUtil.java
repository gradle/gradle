package org.gradle.api.changedetection.digest;

import java.security.MessageDigest;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
class DefaultDigesterUtil implements DigesterUtil {
    private final DigesterUtilStrategy strategy;

    DefaultDigesterUtil(DigesterUtilStrategy strategy) {
        if ( strategy == null ) throw new IllegalArgumentException("strategy is null!");
        
        this.strategy = strategy;
    }

    /**
     * Calls digestFile on the selected strategy.
     *
     * This method throws an IllegalArgumentException when:
     * - strategyName is null
     * - digester is null
     * - file is null
     * - file doesn't exists
     * - file is not a file
     * - strategyName is not supported
     *
     * @param digester The digester to update.
     * @param file The file that needs it's digest calculated.
     */
    public void digestFile(final MessageDigest digester, final File file) {
        if ( digester == null ) throw new IllegalArgumentException("digester is null!");
        if ( file == null ) throw new IllegalArgumentException("file is null!");
        if ( !file.exists() ) throw new IllegalArgumentException("file ("+file.getAbsolutePath()+") doesn't exist!");
        if ( !file.isFile() ) throw new IllegalArgumentException("file ("+file.getAbsolutePath()+") is not a file!");

        strategy.digestFile(digester, file);
    }

    /**
     * Calls digestDirectory on the selected stategy.
     *
     * This method throws an IllegalArgumentException when:
     * - strategyName is null
     * - digester is null
     * - directory is null
     * - directory doesn't exists
     * - directory is not a directory
     * - strategyName is not supported
     *
     * @param digester The digester to update.
     * @param directory The directory that needs it's digest calculated.
     * @param directorySize The size of the directory to use for digest calculation.
     */
    public void digestDirectory(MessageDigest digester, File directory, long directorySize) {
        if ( digester == null ) throw new IllegalArgumentException("digester is null!");
        if ( directory == null ) throw new IllegalArgumentException("directory is null!");
        if ( !directory.exists() ) throw new IllegalArgumentException("directory ("+directory.getAbsolutePath()+") doesn't exist!");
        if ( !directory.isDirectory() ) throw new IllegalArgumentException("directory ("+directory.getAbsolutePath()+") is not a directory!");

        strategy.digestDirectory(digester, directory, directorySize);
    }
}
