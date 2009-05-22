package org.gradle.api.changedetection.state;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.io.IoFactory;

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.FileWriter;

/**
 * State File Format:
 *
 * ( filename ${newLine}
 *   digest ${newLine} )*
 *
 * @author Tom Eyckmans
 */
class StateFileWriter {
    private final IoFactory ioFactory;
    private final File stateFile;

    private BufferedWriter fileWriter;

    StateFileWriter(IoFactory ioFactory, File stateFile) {
        if ( ioFactory == null ) throw new IllegalArgumentException("ioFactory is null!");
        if ( stateFile == null ) throw new IllegalArgumentException("stateFile is null!");
        
        this.ioFactory = ioFactory;
        this.stateFile = stateFile;
    }

    public File getStateFile() {
        return stateFile;
    }

    public void addDigest(final String key, final String digest) throws IOException {
        if ( key == null ) throw new IllegalArgumentException("key is null");
        if ( StringUtils.isEmpty(digest) ) throw new IllegalArgumentException("digest is empty");
        
        if ( fileWriter == null )
            fileWriter = ioFactory.createBufferedWriter(stateFile);

        fileWriter.write(key);
        fileWriter.newLine();
        fileWriter.write(digest);
        fileWriter.newLine();
    }

    public void lastFileDigestAdded() throws IOException {
        if ( fileWriter != null )
            fileWriter.flush();
    }

    public void close() {
        IOUtils.closeQuietly(fileWriter);
    }
}
