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
package org.gradle.logging.internal;

import org.gradle.api.logging.LoggingOutput;

import java.io.OutputStream;

public interface LoggingOutputInternal extends LoggingOutput {
    /**
     * Add standard output and error as logging destinations.
     */
    void addStandardOutputAndError();

    /**
     * Removes standard output and error from logging destinations.
     */
    void removeStandardOutputAndError();

    /**
     * Adds the console as logging destination, if available.
     * removes standard output and/or error as a side-effect
     */
    void attachConsole(boolean colorOutput);

    /**
     * Adds a console as a logging destination. Assumes the given output stream is attached to an ANSI aware console.
     * removes standard output and/or error as a side-effect
     */
    void attachConsole(OutputStream outputStream);

    void addStandardOutputListener(OutputStream outputStream);

    void addStandardErrorListener(OutputStream outputStream);

    void addOutputEventListener(OutputEventListener listener);

    void removeOutputEventListener(OutputEventListener listener);

    /**
     * Removes all non-standard output event listeners (also the ones attached with attachConsole)
     */
    void removeAllOutputEventListeners();
}
