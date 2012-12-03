/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.io.IOUtils.closeQuietly;

/**
 * by Szczepan Faber, created at: 11/19/12
 */
public class CachingFileWriter {

    final LinkedHashMap<File, DataOutputStream> openFiles = new LinkedHashMap<File, DataOutputStream>();
    private final int openFilesCount;

    public CachingFileWriter(int openFilesCount) {
        this.openFilesCount = openFilesCount;
    }

    public void writeUTF(File file, String text) {
        DataOutputStream out;
        try {
            if (openFiles.containsKey(file)) {
                out = openFiles.get(file);
            } else {
                out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file, true)));
                openFiles.put(file, out);
                if (openFiles.size() > openFilesCount) {
                    //remove first
                    Iterator<Map.Entry<File, DataOutputStream>> iterator = openFiles.entrySet().iterator();
                    close(iterator.next().getValue(), file.toString());
                    iterator.remove();
                }
            }
            out.writeUTF(text);
        } catch (IOException e) {
            cleanUpQuietly();
            throw new RuntimeException("Problems writing to file: " + file, e);
        }
    }

    public void close(File file) {
        Closeable c = openFiles.remove(file);
        if (c != null) { //could be already closed
            try {
                close(c, file.toString());
            } finally {
                cleanUpQuietly();
            }
        }
    }

    public void closeAll() {
        try {
            for (Map.Entry<File, DataOutputStream> entry : openFiles.entrySet()) {
                close(entry.getValue(), entry.getKey().toString());
            }
        } finally {
            cleanUpQuietly();
        }
    }

    private void cleanUpQuietly() {
        for (OutputStream o : openFiles.values()) {
            closeQuietly(o);
        }
        openFiles.clear();
    }

    private void close(Closeable c, String displayName) {
        try {
            c.close();
        } catch (IOException e) {
            throw new RuntimeException("Problems closing " + displayName, e);
        }
    }
}