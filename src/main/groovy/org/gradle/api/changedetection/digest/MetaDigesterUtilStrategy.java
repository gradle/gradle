package org.gradle.api.changedetection.digest;

import java.security.MessageDigest;
import java.io.File;

/**
 * The DigesterUtilStrategyNames.META DigesterUtilStrategy implementation.
 *
 * @author Tom Eyckmans
 */
class MetaDigesterUtilStrategy implements DigesterUtilStrategy{
    /**
     * Calls update on the digester with the byte values of:
     * - the absolute path of the file
     * - the last modified date of the file
     * - the file size
     *
     * @param digester The digester to update.
     * @param file The file that needs it's digest calculated.
     */
    public void digestFile(MessageDigest digester, File file) {
        digester.update(file.getAbsolutePath().getBytes());
        digester.update(((Long)file.lastModified()).byteValue());
        digester.update(((Long)file.length()).byteValue());
    }

    /**
     * Calls update on the digester with the byte values of:
     * - the absolute path of the directory
     * - the last modified date of the directory
     * - the provided directory size
     *
     * @param digester The digester to update.
     * @param directory The directory that needs it's digest calculated.
     * @param directorySize The directory size that needs to be used during digest calculation.
     */
    public void digestDirectory(MessageDigest digester, File directory, long directorySize) {
        digester.update(directory.getAbsolutePath().getBytes());
        digester.update(((Long)directory.lastModified()).byteValue());
        digester.update(((Long)directorySize).byteValue());
    }
}
