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
     * Adds System.out and System.err as logging destinations.
     */
    void attachSystemOutAndErr();

    /**
     * Adds the current processes' stdout and stderr as logging destinations. The output will also include colorized text and status bar when one of these
     * is connected to a console.
     *
     * <p>Removes standard output and/or error as a side-effect.
     */
    void attachProcessConsole(boolean colorOutput);

    /**
     * Adds the given {@link java.io.OutputStream} as a logging destination. The stream receives stdout and stderr logging formatted according to the current logging settings
     * and encoded using the system character encoding. The output also includes colorized text and status bar encoded using ANSI control sequences.
     *
     * <p>Removes standard output and/or error as a side-effect.
     */
    void attachAnsiConsole(OutputStream outputStream);

    /**
     * Adds the given {@link java.io.OutputStream} as a logging destination. The stream receives stdout logging formatted according to the current logging settings and
     * encoded using the system character encoding.
     */
    void addStandardOutputListener(OutputStream outputStream);

    /**
     * Adds the given {@link java.io.OutputStream} as a logging destination. The stream receives stderr logging formatted according to the current logging settings and
     * encoded using the system character encoding.
     */
    void addStandardErrorListener(OutputStream outputStream);

    void addOutputEventListener(OutputEventListener listener);

    void removeOutputEventListener(OutputEventListener listener);

    /**
     * Removes all non-standard output event listeners (also the ones attached with attachConsole)
     */
    void removeAllOutputEventListeners();
}
