/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Hans Dockter
 */
public class InputStreamClassLoader extends ClassLoader {

    private static int MAXCLASSFILESIZE = 65 * 1024;

    private static int STREAMREADCHUNKSIZE = 512;

    public Class loadClass(String name, InputStream inStream)
            throws ClassNotFoundException, IOException {
        if ((null == name) || (null == inStream))
            throw new ClassNotFoundException();
        byte[] buffer = new byte[MAXCLASSFILESIZE];
        int byteCount = 0;
        int readResult = 0;
        do {
            byteCount += readResult;
            readResult = inStream.read(buffer, byteCount, STREAMREADCHUNKSIZE);
        } while ((readResult != -1) && (byteCount < MAXCLASSFILESIZE));
        if ((byteCount == MAXCLASSFILESIZE) && (inStream.read() != -1))
            throw new IOException();
        return defineClass(name, buffer, 0, byteCount);
    }
}
