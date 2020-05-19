/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.process.internal.util;

import org.gradle.internal.os.OperatingSystem;

import java.util.List;

public class LongCommandLineDetectionUtil {
    // See http://msdn.microsoft.com/en-us/library/windows/desktop/ms682425(v=vs.85).aspx
    public static final int MAX_COMMAND_LINE_LENGTH_WINDOWS = 32767;
    // Derived from default when running getconf ARG_MAX in OSX
    public static final int MAX_COMMAND_LINE_LENGTH_OSX = 262144;
    // Dervied from MAX_ARG_STRLEN as per http://man7.org/linux/man-pages/man2/execve.2.html
    public static final int MAX_COMMAND_LINE_LENGTH_NIX = 131072;
    private static final String WINDOWS_LONG_COMMAND_EXCEPTION_MESSAGE = "The filename or extension is too long";
    private static final String NIX_LONG_COMMAND_EXCEPTION_MESSAGE = "error=7, Argument list too long";

    public static boolean hasCommandLineExceedMaxLength(String command, List<String> arguments) {
        int commandLineLength = command.length() + arguments.stream().map(String::length).reduce(Integer::sum).orElse(0) + arguments.size();
        return commandLineLength > getMaxCommandLineLength();
    }

    private static int getMaxCommandLineLength() {
        int defaultMax = MAX_COMMAND_LINE_LENGTH_NIX;
        if (OperatingSystem.current().isMacOsX()) {
            defaultMax = MAX_COMMAND_LINE_LENGTH_OSX;
        } else if (OperatingSystem.current().isWindows()) {
            defaultMax = MAX_COMMAND_LINE_LENGTH_WINDOWS;
        }
        // in chars
        return Integer.getInteger("org.gradle.internal.cmdline.max.length", defaultMax);
    }

    public static boolean hasCommandLineExceedMaxLengthException(Throwable failureCause) {
        Throwable cause = failureCause;
        do {
            if (cause.getMessage().contains(WINDOWS_LONG_COMMAND_EXCEPTION_MESSAGE) || cause.getMessage().contains(NIX_LONG_COMMAND_EXCEPTION_MESSAGE)) {
                return true;
            }
        } while ((cause = cause.getCause()) != null);

        return false;
    }
}
