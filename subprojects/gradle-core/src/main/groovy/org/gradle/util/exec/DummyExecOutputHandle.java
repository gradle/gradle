package org.gradle.util.exec;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class DummyExecOutputHandle implements ExecOutputHandle {

    public void handleOutputLine(String outputLine) throws IOException {

    }

    public void endOutput() throws IOException {

    }

    public boolean execOutputHandleError(Throwable t) {
        return true;
    }
}
