package org.gradle.util.exec;

import java.io.*;

/**
 * @author Tom Eyckmans
 */
public class StreamWriterExecOutputHandle implements ExecOutputHandle {

    private final BufferedWriter target;
    private final boolean directFlush;

    public StreamWriterExecOutputHandle(Writer target) {
        this.target = new BufferedWriter(target);
        this.directFlush = false;
    }

    public StreamWriterExecOutputHandle(BufferedWriter target) {
        this.target = target;
        this.directFlush = false;
    }

    public StreamWriterExecOutputHandle(OutputStream target) {
        this.target = new BufferedWriter(new OutputStreamWriter(target));
        this.directFlush = false;
    }

    public StreamWriterExecOutputHandle(Writer target, boolean directFlush) {
        this.target = new BufferedWriter(target);
        this.directFlush = directFlush;
    }

    public StreamWriterExecOutputHandle(BufferedWriter target, boolean directFlush) {
        this.target = target;
        this.directFlush = directFlush;
    }

    public StreamWriterExecOutputHandle(OutputStream target, boolean directFlush) {
        this.target = new BufferedWriter(new OutputStreamWriter(target));
        this.directFlush = directFlush;
    }

    public void handleOutputLine(String outputLine) throws IOException {
        target.write(outputLine);
        target.newLine();
        if (directFlush)
            target.flush();
    }

    public boolean execOutputHandleError(Throwable t) {
        t.printStackTrace();
        return true;
    }

    public BufferedWriter getTarget() {
        return target;
    }
}
