/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ExecOutputHandleRunner implements Runnable {
    private final static Logger LOGGER = Logging.getLogger(ExecOutputHandleRunner.class);

    private final String displayName;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final int bufferSize;

    public ExecOutputHandleRunner(String displayName, InputStream inputStream, OutputStream outputStream) {
        this(displayName, inputStream, outputStream, 2048);
    }

    ExecOutputHandleRunner(String displayName, InputStream inputStream, OutputStream outputStream, int bufferSize) {
        this.displayName = displayName;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.bufferSize = bufferSize;
    }

    public void run() {
        byte[] buffer = new byte[bufferSize];
        try {
            while (true) {
                int nread = inputStream.read(buffer);
                if (nread < 0) {
                    break;
                }
                outputStream.write(buffer, 0, nread);
                outputStream.flush();
            }
            CompositeStoppable.stoppable(inputStream, outputStream).stop();
        } catch (Throwable t) {
            LOGGER.error(String.format("Could not %s.", displayName), t);
        }
    }

    public void closeInput() throws IOException {
        inputStream.close();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
