/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.process.internal.streams;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.operations.BuildOperationIdentifierPreservingRunnable;
import org.gradle.process.internal.StreamsHandler;
import org.gradle.util.DisconnectableInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

public class StreamsForwarder implements StreamsHandler {

    private final OutputStream standardOutput;
    private final OutputStream errorOutput;
    private final InputStream input;
    private final boolean readErrorStream;

    private Executor executor;
    private ExecOutputHandleRunner standardOutputReader;
    private ExecOutputHandleRunner standardErrorReader;
    private ExecOutputHandleRunner standardInputWriter;

    public StreamsForwarder(OutputStream standardOutput, OutputStream errorOutput, InputStream input, boolean readErrorStream) {
        this.standardOutput = standardOutput;
        this.errorOutput = errorOutput;
        this.input = input;
        this.readErrorStream = readErrorStream;
    }

    @Override
    public void connectStreams(Process process, String processName, Executor executor) {
        this.executor = executor;

        /*
            There's a potential problem here in that DisconnectableInputStream reads from input in the background.
            This won't automatically stop when the process is over. Therefore, if input is not closed then this thread
            will run forever. It would be better to ensure that this thread stops when the process does.
         */
        InputStream instr = new DisconnectableInputStream(input);

        standardOutputReader = new ExecOutputHandleRunner("read standard output of " + processName,
                process.getInputStream(), standardOutput);
        standardErrorReader = new ExecOutputHandleRunner("read error output of " + processName, process.getErrorStream(),
                errorOutput);
        standardInputWriter = new ExecOutputHandleRunner("write standard input to " + processName,
                instr, process.getOutputStream());
    }

    public void start() {
        executor.execute(standardInputWriter);
        if (readErrorStream) {
            executor.execute(wrapInBuildOperation(standardErrorReader));
        }
        executor.execute(wrapInBuildOperation(standardOutputReader));
    }

    private Runnable wrapInBuildOperation(Runnable runnable) {
        return new BuildOperationIdentifierPreservingRunnable(runnable);
    }

    public void stop() {
        try {
            standardInputWriter.closeInput();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        standardInputWriter.waitForCompletion();
        if (readErrorStream) {
            standardErrorReader.waitForCompletion();
        }
        standardOutputReader.waitForCompletion();
    }
}
