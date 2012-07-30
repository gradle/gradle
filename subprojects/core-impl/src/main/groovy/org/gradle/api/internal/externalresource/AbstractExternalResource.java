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
package org.gradle.api.internal.externalresource;

import org.apache.ivy.plugins.repository.Resource;

import java.io.*;

public abstract class AbstractExternalResource implements ExternalResource {
    // according to tests by users, 64kB seems to be a good value for the buffer used during copy
    // further improvements could be obtained using NIO API
    private static final int BUFFER_SIZE = 64 * 1024;

    public void writeTo(File destination) throws IOException {
        FileOutputStream output = new FileOutputStream(destination);
        writeTo(output);
        output.close();
    }

    public void writeTo(OutputStream output) throws IOException {
        InputStream input = openStream();
        try {
            copy(input, output);
        } finally {
            input.close();
        }
    }


    public Resource clone(String cloneName) {
        throw new UnsupportedOperationException();
    }

    public void close() throws IOException {
    }

    public static void copy(InputStream src, OutputStream dest)
            throws IOException {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int c;
            while ((c = src.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("transfer interrupted");
                }
                dest.write(buffer, 0, c);
            }
            try {
                dest.flush();
            } catch (IOException ex) {
                // ignore
            }

            // close the streams
            src.close();
            dest.close();
        } finally {
            try {
                src.close();
            } catch (IOException ex) {
                // ignore
            }
            try {
                dest.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }
}
