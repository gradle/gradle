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

import java.io.File;
import java.io.IOException;

import static com.sun.jna.platform.win32.WinBase.*;
import static org.gradle.internal.nativeplatform.jna.Kernel32.INSTANCE;

/**
 * Uses the native Windows CreateProcessW() function, instead of Java's Process.
 * This is so we can create a fully detached process. This prevents, for example, the child process inheriting our stdout
 * and stderr handles, so that our (java) parent process does not block waiting for our stdout/stderr to complete after
 * this process exits.
 *
 * Of course, this also means that we can't use the child process's stdout/stderr/stdin streams for anything useful.
 *
 * TODO - figure out how to close the handles inherited from our parent process
 */
public class WindowsProcessStarter {

    public void start(File dir, String command) throws IOException {
        Kernel32 kernel32 = INSTANCE;
        STARTUPINFO startupInfo = new STARTUPINFO();
        PROCESS_INFORMATION.ByReference processInformation = new PROCESS_INFORMATION.ByReference();
        if (!kernel32.CreateProcess(null, command, null, null, false, new DWORD(DETACHED_PROCESS), null,
                dir.getAbsolutePath(), startupInfo, processInformation)) {
            throw new IOException("Could not start process. Errno: " + kernel32.GetLastError());
        }
        kernel32.CloseHandle(processInformation.hProcess);
        kernel32.CloseHandle(processInformation.hThread);
    }
}
