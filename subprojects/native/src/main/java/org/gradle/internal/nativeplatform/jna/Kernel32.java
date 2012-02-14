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

import com.sun.jna.Native;
import com.sun.jna.WString;

/**
* Windows' Kernel32
*/
public interface Kernel32 extends com.sun.jna.platform.win32.Kernel32 {

    //CHECKSTYLE:OFF

    Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);

    boolean SetEnvironmentVariableW(WString lpName, WString lpValue);

    boolean SetCurrentDirectoryW(WString lpPathName);

    int GetCurrentDirectoryW(int nBufferLength, char[] lpBuffer);

    //CHECKSTYLE:ON
}
