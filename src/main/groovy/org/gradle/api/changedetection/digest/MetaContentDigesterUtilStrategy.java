package org.gradle.api.changedetection.digest;

import org.gradle.api.GradleException;
import org.apache.commons.io.IOUtils;

import java.security.MessageDigest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The DigesterUtilStrategyNames.META_CONTENT DigesterUtilStrategy implementation.
 *
 * The DigesterUtilStrategyNames.META_CONTENT extends the DigesterUtilStrategyNames.META DigesterUtilStrategy implementation
 * and redefines the digestFile method to include the file content.
 *
 * @author Tom Eyckmans
 */
class MetaContentDigesterUtilStrategy extends MetaDigesterUtilStrategy {
    /**
     * The byte array buffer size used to update the digester with the file content.
     *
     * Defaults to 1024.
     */
    private final AtomicInteger fileContentBufferSize = new AtomicInteger(1024);

    /**
     * Creates an instance with the default fileContentBufferSize.
     */
    MetaContentDigesterUtilStrategy() {
    }

    /**
     * Creates an instance with a custom fileContentBufferSize.
     *
     * @param fileContentBufferSize The custom fileContentBufferSize.
     * @exception IllegalArgumentException When the fileContentBuffer size provided <= 0.
     */
    public MetaContentDigesterUtilStrategy(int fileContentBufferSize) {
        setFileContentBufferSize(fileContentBufferSize);
    }

    /**
     * Calls {@see MetaDigesterUtilStrategy.digestFile} and in addition to the DigesterUtilStrategyNames.META
     * this method also include the byte content of the file in the digest calculation.
     *
     * This method uses a byte array to buffer the file content. The size of this buffer can be controlled by the
     * fileContentBufferSize attribute. 
     *
     * @param digester The digester to update.
     * @param file The file that needs it's digest calculated.
     */
    public void digestFile(MessageDigest digester, File file) {
        super.digestFile(digester, file);

        FileInputStream fileStream = null;
        try {
            fileStream = new FileInputStream(file);

            final byte[] fileContentBuffer = new byte[fileContentBufferSize.get()];
            while ( fileStream.read(fileContentBuffer) != -1 ) {
                digester.update(fileContentBuffer);
            }
        }
        catch (IOException e) {
            throw new GradleException("failed to add content to file digest", e);
        }
        finally {
            IOUtils.closeQuietly(fileStream);
        }
    }

    /**
     * Update the fileContentBufferSize.
     *
     * @param fileContentBufferSize The new fileContentBufferSize to use.
     * @exception IllegalArgumentException When the fileContentBuffer size provided <= 0.
     */
    public void setFileContentBufferSize(int fileContentBufferSize) {
        if ( fileContentBufferSize <= 0 ) throw new IllegalArgumentException("fileContentBufferSize <= 0!");
        this.fileContentBufferSize.set(fileContentBufferSize);
    }
}
