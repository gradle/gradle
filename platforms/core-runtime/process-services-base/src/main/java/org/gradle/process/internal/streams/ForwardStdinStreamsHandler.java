/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.UncheckedException;
import org.gradle.util.internal.DisconnectableInputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

/**
 * Forwards the contents of an {@link InputStream} to the process' stdin
 */
public class ForwardStdinStreamsHandler implements StreamsHandler {
    private final InputStream input;
    private final CountDownLatch completed = new CountDownLatch(1);
    // Set by connectStreams()
    private @Nullable Executor executor;
    private @Nullable ExecOutputHandleRunner standardInputWriter;

    public ForwardStdinStreamsHandler(InputStream input) {
        this.input = input;
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
        standardInputWriter = new ExecOutputHandleRunner("write standard input to " + processName, instr, process.getOutputStream(), completed::countDown);
    }

    @Override
    public void start() {
        //TODO IllegalStateException might be the better option here. In the interest of not changing the behavior, we keep this
        requireNonNull(executor).execute(requireNonNull(standardInputWriter));
    }

    @Override
    public void removeStartupContext() {
    }

    @Override
    public void stop() {
        disconnect();
        try {
            completed.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void disconnect() {
        try {
            requireNonNull(standardInputWriter).closeInput();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
