/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.internal.nativeplatform.jna;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.gradle.internal.nativeplatform.jna.Kernel32.*;

/**
 * Uses jna to make the stream handles 'uninheritable'. 
 * This way we can achieve a fully detached process on windows.
 * Without that, if the process was spawning child processes on windows (for example gradle build daemon) it waited until the child has completed.
 * This is undesired because sometimes the child process is a long-running process but the parent process is a short-running process.
 */
public class WindowsHandlesManipulator {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowsHandlesManipulator.class);

    /**
     * Makes the standard streams handles 'uninheritable' for the current process.
     *
     * @throws ProcessInitializationException when the operation fails
     */
    public void uninheritStandardStreams() throws ProcessInitializationException {
        Kernel32 kernel32 = INSTANCE;

        try {
            uninheritStream(kernel32, Kernel32.STD_INPUT_HANDLE);
            uninheritStream(kernel32, Kernel32.STD_OUTPUT_HANDLE);
            uninheritStream(kernel32, Kernel32.STD_ERROR_HANDLE);
        } catch (Exception e) {
            throw new ProcessInitializationException("Failed to configure the standard stream handles to be 'uninheritable'.", e);
        }
    }

    private void uninheritStream(Kernel32 kernel32, int stdInputHandle) throws IOException {
        HANDLE streamHandle = kernel32.GetStdHandle(stdInputHandle);
        if (streamHandle == null) {
            // We're not attached to a stdio (eg Desktop application). Ignore.
            return;
        }
        makeUninheritable(kernel32, streamHandle);
    }

    private void makeUninheritable(Kernel32 kernel32, HANDLE streamHandle) throws IOException {
        if (streamHandle.equals(INVALID_HANDLE_VALUE)) {
            throw new IOException("Invalid handle. Errno: " + kernel32.GetLastError());
        }
        boolean ok = kernel32.SetHandleInformation(streamHandle, HANDLE_FLAG_INHERIT, 0);
        if (!ok) {
            int setHandleError = kernel32.GetLastError();
            if (setHandleError == ERROR_INVALID_PARAMETER) {
                // Didn't get a valid handle: ignore.
                LOGGER.debug("Invalid parameter attempting to uninherit stream - child process may remain attached.");
                return;
            }
            if (setHandleError == ERROR_INVALID_HANDLE) {
                LOGGER.debug("Invalid handle attempting to uninherit stream - child process may remain attached.");
                return;
            }
            throw new IOException("Could not set flag on handle. Errno: " + kernel32.GetLastError());
        }
    }

    public static class ProcessInitializationException extends RuntimeException {
        public ProcessInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
