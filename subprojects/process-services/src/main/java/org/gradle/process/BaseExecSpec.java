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
package org.gradle.process;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Specifies options for launching a child process.
 */
public interface BaseExecSpec extends ProcessForkOptions {
    /**
     * Sets whether a non-zero exit value is ignored, or an exception thrown.
     *
     * @param ignoreExitValue whether a non-zero exit value is ignored, or an exception thrown
     * @return this
     */
    BaseExecSpec setIgnoreExitValue(boolean ignoreExitValue);

    /**
     * Tells whether a non-zero exit value is ignored, or an exception thrown. Defaults to <code>false</code>.
     *
     * @return whether a non-zero exit value is ignored, or an exception thrown
     */
    boolean isIgnoreExitValue();

    /**
     * Sets the standard input stream for the process executing the command. The stream is closed after the process
     * completes.
     *
     * @param inputStream The standard input stream for the process. Must not be null.
     * @return this
     */
    BaseExecSpec setStandardInput(InputStream inputStream);

    /**
     * Returns the standard input stream for the process executing the command. The stream is closed after the process
     * completes. Defaults to an empty stream.
     *
     * @return The standard input stream.
     */
    InputStream getStandardInput();

    /**
     * Sets the output stream to consume standard output from the process executing the command. The stream is closed
     * after the process completes.
     *
     * @param outputStream The standard output stream for the process. Must not be null.
     * @return this
     */
    BaseExecSpec setStandardOutput(OutputStream outputStream);

    /**
     * Returns the output stream to consume standard output from the process executing the command. Defaults to {@code
     * System.out}.
     *
     * @return The output stream
     */
    OutputStream getStandardOutput();

    /**
     * Sets the output stream to consume standard error from the process executing the command. The stream is closed
     * after the process completes.
     *
     * @param outputStream The standard output error stream for the process. Must not be null.
     * @return this
     */
    BaseExecSpec setErrorOutput(OutputStream outputStream);

    /**
     * Returns the output stream to consume standard error from the process executing the command. Default to {@code
     * System.err}.
     *
     * @return The error output stream.
     */
    OutputStream getErrorOutput();

    /**
     * Returns the full command line, including the executable plus its arguments.
     *
     * @return The full command line, including the executable plus its arguments
     */
    List<String> getCommandLine();
}