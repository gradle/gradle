package org.gradle.util.exec;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public interface ExecOutputHandle {
    /**
     * Called when a line of output has been read from an exec'd process.
     */
    void handleOutputLine(String outputLine) throws IOException;

    /**
     * Called when the end of output from an exec'd process has been reached.
     */
    void endOutput() throws IOException;

    /**
     * Called when an exeception occurs reading the output from an exec'd process.
     * @return true if output handling should continue, false if output handling should end.
     */
    boolean execOutputHandleError(Throwable t);
}
