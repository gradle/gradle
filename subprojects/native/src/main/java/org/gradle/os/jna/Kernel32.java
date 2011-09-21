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

package org.gradle.os.jna;

import com.sun.jna.*;

/**
* Windows' Kernel32
*/
public interface Kernel32 extends Library {

    //CHECKSTYLE:OFF

    Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);

    // Process creation flags
    int DETACHED_PROCESS = 0x00000008;

    int GetLastError();

    boolean CloseHandle(HANDLE hObject);

    boolean CreateProcessW(WString lpApplicationName, WString lpCommandLine, SecurityAttributes lpProcessAttributes,
                           SecurityAttributes lpThreadAttributes, boolean bInheritHandles, int dwCreationFlags,
                           Pointer lpEnvironment, WString lpCurrentDirectory, StartupInfo lpStartupInfo,
                           ProcessInfo lpProcessInformation);

    boolean SetEnvironmentVariableW(WString lpName, WString lpValue);

    boolean SetCurrentDirectoryW(WString lpPathName);

    int GetCurrentDirectoryW(int nBufferLength, char[] lpBuffer);

    int GetCurrentProcessId();

    class HANDLE extends PointerType {
        public HANDLE() {
        }

        public HANDLE(Pointer p) {
            super(p);
        }
    }

    class SecurityAttributes extends Structure {
        public int nLength;
        public Pointer lpSecurityDescriptor;
        public boolean bInheritHandle;

        public SecurityAttributes() {
            nLength = size();
        }
    }

    class StartupInfo extends Structure {
        public int cb;
        public WString lpReserved;
        public WString lpDesktop;
        public WString lpTitle;
        public int dwX;
        public int dwY;
        public int dwXSize;
        public int dwYSize;
        public int dwXCountChars;
        public int dwYCountChars;
        public int dwFillAttribute;
        public int dwFlags;
        public short wShowWindow;
        public short cbReserved2;
        public Pointer lpReserved2;
        public HANDLE hStdInput;
        public HANDLE hStdOutput;
        public HANDLE hStdError;

        public StartupInfo() {
            cb = size();
        }
    }

    class ProcessInfo extends Structure {
        public HANDLE hProcess;
        public HANDLE hThread;
        public int dwProcessId;
        public int dwThreadId;
    }

    //CHECKSTYLE:ON
}
