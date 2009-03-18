package org.gradle.util.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author Tom Eyckmans
 */
public class ExecOutputHandleRunner implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(ExecOutputHandleRunner.class);

    private final BufferedReader inputReader;
    private final ExecOutputHandle execOutputHandle;

    public ExecOutputHandleRunner(final InputStream inputStream, final ExecOutputHandle execOutputHandle) {
        this.inputReader = new BufferedReader(new InputStreamReader(inputStream));
        this.execOutputHandle = execOutputHandle;
    }

    public void run() {
        boolean keepHandling = true;

        try {
            String outputLine;
            while (keepHandling) {
                try {
                    outputLine = inputReader.readLine();
                } catch (Throwable t) {
                    keepHandling = execOutputHandle.execOutputHandleError(t);
                    continue;
                }

                if (outputLine == null) {
                    keepHandling = false;
                } else {
                    execOutputHandle.handleOutputLine(outputLine);
                }
            }
            execOutputHandle.endOutput();
        } catch (Throwable t) {
            logger.error("Could not process output from exec'd process.", t);
        }
    }
}
