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
 *
 * @author Hans Dockter
 */
public interface BaseExecSpec extends ProcessForkOptions {
    /**
     * Sets whether an exit value different from zero should be ignored. In case it is not ignored, an exception is
     * thrown in case of such an exit value.
     *
     * @param ignoreExitValue whether to ignore the exit value or not
     *
     * @return this
     */
    BaseExecSpec setIgnoreExitValue(boolean ignoreExitValue);

    /**
     * Returns whether an exit value different from zero should be ignored. In case it is not ignored, an exception is
     * thrown in case of such an exit value. Defaults to <code>false</code>.
     */
    boolean isIgnoreExitValue();

    /**
     * Sets the standard input stream for the process executing the command. The stream is closed after the process
     * completes.
     * 
     * @param inputStream The standard input stream for the command process.
     *
     * @return this
     */
    BaseExecSpec setStandardInput(InputStream inputStream);

    /**
     * Returns the standard input stream for the process executing the command.
     */
    InputStream getStandardInput();

    /**
     * Sets the standard output stream for the process executing the command. The stream is closed after the process
     * completes.
     *
     * @param outputStream The standard output stream for the command process.
     *
     * @return this
     */
    BaseExecSpec setStandardOutput(OutputStream outputStream);

    /**
     * Sets the error output stream for the process executing the command. The stream is closed after the process
     * completes.
     *
     * @param outputStream The standard output error stream for the command process.
     *
     * @return this
     */
    BaseExecSpec setErrorOutput(OutputStream outputStream);

    /**
     * Returns the command plus its arguments.
     */
    List<String> getCommandLine();
}