/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.changedetection.state;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.io.IoFactory;

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * State File Format:
 *
 * ( filename ${newLine} digest ${newLine} )*
 *
 * @author Tom Eyckmans
 */
class StateFileWriter {
    private final IoFactory ioFactory;
    private final File stateFile;

    private BufferedWriter fileWriter;

    StateFileWriter(IoFactory ioFactory, File stateFile) {
        if (ioFactory == null) {
            throw new IllegalArgumentException("ioFactory is null!");
        }
        if (stateFile == null) {
            throw new IllegalArgumentException("stateFile is null!");
        }

        this.ioFactory = ioFactory;
        this.stateFile = stateFile;
    }

    public File getStateFile() {
        return stateFile;
    }

    public void addDigest(final String key, final String digest) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }
        if (StringUtils.isEmpty(digest)) {
            throw new IllegalArgumentException("digest is empty");
        }

        if (fileWriter == null) {
            fileWriter = ioFactory.createBufferedWriter(stateFile);
        }

        fileWriter.write(key);
        fileWriter.newLine();
        fileWriter.write(digest);
        fileWriter.newLine();
    }

    public void lastFileDigestAdded() throws IOException {
        if (fileWriter != null) {
            fileWriter.flush();
        }
    }

    public void close() {
        IOUtils.closeQuietly(fileWriter);
    }
}
