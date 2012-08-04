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

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.win32.W32APIOptions;

/**
* Windows' Kernel32
*/
public interface Kernel32 extends Library {

    //CHECKSTYLE:OFF

    Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class, W32APIOptions.UNICODE_OPTIONS);

    int STD_INPUT_HANDLE = -10;
    int STD_OUTPUT_HANDLE = -11;
    int STD_ERROR_HANDLE = -12;
    int HANDLE_FLAG_INHERIT = 1;
    int ERROR_INVALID_HANDLE = 6;
    int ERROR_INVALID_PARAMETER = 87;
    HANDLE INVALID_HANDLE_VALUE = new HANDLE(new Pointer(-1));

    int GetLastError();

    boolean SetEnvironmentVariable(String lpName, String lpValue);

    HANDLE GetStdHandle(int stdHandle);

    boolean SetHandleInformation(HANDLE handle, int dwMask, int dwFlags);

    boolean SetCurrentDirectory(String lpPathName);

    int GetCurrentDirectory(int nBufferLength, char[] lpBuffer);

    int GetCurrentProcessId();

    class HANDLE extends PointerType {
        public HANDLE() {
        }

        public HANDLE(Pointer p) {
            super(p);
        }
    }

    //CHECKSTYLE:ON
}
