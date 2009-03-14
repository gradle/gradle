package org.gradle.util.exec;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author Tom Eyckmans
 */
public class ExecOutputHandleRunner implements Runnable {

    private final BufferedReader inputReader;
    private final ExecOutputHandle execOutputHandle;

    public ExecOutputHandleRunner(final InputStream inputStream, final ExecOutputHandle execOutputHandle) {
        this.inputReader = new BufferedReader(new InputStreamReader(inputStream));
        this.execOutputHandle = execOutputHandle;
    }

    public void run() {
        boolean keepHandling = true;

        try {
            String outputLine = null;
            while ( keepHandling ) {
                try {
                    outputLine = inputReader.readLine();
                    if ( outputLine == null )
                        keepHandling = false;
                    else {
                        execOutputHandle.handleOutputLine(outputLine);
                    }
                }
                catch ( Throwable t ) {
                    keepHandling = execOutputHandle.execOutputHandleError(t);
                }
            }
        }
        catch ( Throwable t ) {
            t.printStackTrace();
        }
    }
}
