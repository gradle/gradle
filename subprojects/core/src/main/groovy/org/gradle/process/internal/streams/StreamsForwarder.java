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

import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.util.DisconnectableInputStream;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * by Szczepan Faber, created at: 4/17/12
 */
public class StreamsForwarder {

    private final OutputStream standardOutput;
    private final OutputStream errorOutput;
    private final InputStream input;
    private final boolean readErrorStream;
    private ProcessStreamHandler standardInputHandler;

    private StoppableExecutor executor;
    private ExecOutputHandleRunner standardOutputRunner;
    private ExecOutputHandleRunner errorOutputRunner;
    private ExecOutputHandleRunner standardInputRunner;

    public StreamsForwarder(OutputStream standardOutput, OutputStream errorOutput, InputStream input, boolean readErrorStream, ProcessStreamHandler standardInputHandler) {
        this.standardOutput = standardOutput;
        this.errorOutput = errorOutput;
        this.input = input;
        this.readErrorStream = readErrorStream;
        this.standardInputHandler = standardInputHandler;
    }

    public void connectStreams(Process process, String processName) {
        InputStream instr = new DisconnectableInputStream(input);

        standardOutputRunner = new ExecOutputHandleRunner("read standard output of: " + processName,
                process.getInputStream(), standardOutput, standardInputHandler);

        ProcessStreamHandler errorStreamHandler = readErrorStream? new DefaultProcessStreamHandler() : new ClosingProcessStreamHandler();

        errorOutputRunner = new ExecOutputHandleRunner("read error output of: " + processName, process.getErrorStream(),
                errorOutput, errorStreamHandler);

        standardInputRunner = new ExecOutputHandleRunner("write standard input into: " + processName,
                instr, process.getOutputStream(), new DefaultProcessStreamHandler());

        this.executor = new DefaultExecutorFactory().create(String.format("Forward streams with process: %s", processName));
    }

    public void start() {
        executor.execute(standardInputRunner);
        executor.execute(errorOutputRunner);
        executor.execute(standardOutputRunner);
    }

    public void stop() {
        executor.stop();
    }
}