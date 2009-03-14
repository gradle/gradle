package org.gradle.util.exec;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public interface ExecOutputHandle {
    void handleOutputLine(String outputLine) throws IOException;

    boolean execOutputHandleError(Throwable t);
}
