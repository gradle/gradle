/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging;

import org.gradle.api.logging.LoggingOutput;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.io.OutputStream;

/**
 * Allows various logging consumers to be attached to the output of the logging system.
 */
public interface LoggingOutputInternal extends LoggingOutput {
    /**
     * Adds System.out and System.err as logging destinations. The output will include plain text only, with no color or dynamic text.
     */
    void attachSystemOutAndErr();

    /**
     * Adds the current processes' stdout and stderr as logging destinations. The output will also include color and dynamic text when one of these
     * is connected to a console.
     *
     * <p>Removes standard output and/or error as a side-effect.
     */
    void attachProcessConsole(ConsoleOutput consoleOutput);

    /**
     * Adds the given {@link java.io.OutputStream} as a logging destination. The stream receives stdout and stderr logging formatted according to the current logging settings
     * and encoded using the system character encoding. The output also includes color and dynamic text encoded using ANSI control sequences.
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

    /**
     * Adds the given listener as a logging destination.
     */
    @UsedByScanPlugin
    void addOutputEventListener(OutputEventListener listener);

    /**
     * Adds the given listener.
     */
    @UsedByScanPlugin
    void removeOutputEventListener(OutputEventListener listener);
}
