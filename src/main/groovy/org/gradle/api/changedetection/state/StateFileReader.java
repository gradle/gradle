package org.gradle.api.changedetection.state;

import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.io.IoFactory;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
class StateFileReader {
    private final IoFactory ioFactory;
    private final File stateFile;

    private BufferedReader stateFileReader;

    StateFileReader(IoFactory ioFactory, File stateFile) {
        if ( ioFactory == null ) throw new IllegalArgumentException("ioFactory is null!");
        if ( stateFile == null ) throw new IllegalArgumentException("stateFile is null!");

        this.ioFactory = ioFactory;
        this.stateFile = stateFile;
    }

    public StateFileItem readStateFileItem() throws IOException {
        if ( stateFile.exists() ) {
            if ( stateFileReader == null )
                stateFileReader = ioFactory.createBufferedReader(stateFile);

            final String key = stateFileReader.readLine();
            if ( key == null )
                return null;

            final String digest = stateFileReader.readLine();
            if ( digest == null )
                throw new GradleException("state file invalid key ("+key+") did not have a digest value!");
            else
                return new StateFileItem(key, digest);
        }
        else
            return null;
    }

    public void lastStateFileItemRead() {
        IOUtils.closeQuietly(stateFileReader);
    }
}
