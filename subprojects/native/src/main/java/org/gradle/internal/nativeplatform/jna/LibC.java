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

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;

public interface LibC extends Library {
    //CHECKSTYLE:OFF
    public int setenv(String name, String value, int overwrite) throws LastErrorException;
    public int unsetenv(String name) throws LastErrorException;
    public String getcwd(byte[] out, int size) throws LastErrorException;
    public int chdir(String dirAbsolutePath) throws LastErrorException;
    public int getpid();
    public int isatty(int fdes);
    public int chmod(String filename, int mode) throws LastErrorException;
    //CHECKSTYLE:ON
}
