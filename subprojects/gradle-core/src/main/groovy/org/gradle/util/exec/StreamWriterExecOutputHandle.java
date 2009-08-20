/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public void endOutput() throws IOException {
        target.flush();
    }

    public BufferedWriter getTarget() {
        return target;
    }
}
