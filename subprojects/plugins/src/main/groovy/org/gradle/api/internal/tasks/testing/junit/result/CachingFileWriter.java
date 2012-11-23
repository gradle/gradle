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

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 11/19/12
 */
public class CachingFileWriter {

    final LinkedHashMap<File, Writer> openFiles = new LinkedHashMap<File, Writer>();
    private final int openFilesCount;

    public CachingFileWriter(int openFilesCount) {
        this.openFilesCount = openFilesCount;
    }

    public void write(File file, String text) {
        Writer out = null;
        try {
            if (openFiles.containsKey(file)) {
                out = openFiles.get(file);
            } else {
                out = new BufferedWriter(new FileWriter(file, true));
                openFiles.put(file, out);
                if (openFiles.size() > openFilesCount) {
                    //remove first
                    Iterator<Map.Entry<File, Writer>> iterator = openFiles.entrySet().iterator();
                    close(iterator.next().getValue(), file.toString());
                    iterator.remove();
                }
            }
            out.write(text);
        } catch (IOException e) {
            IOUtils.closeQuietly(out);
            throw new RuntimeException("Problems writing to file: " + file, e);
        }
    }

    public void close(File file) {
        Writer w = openFiles.remove(file);
        if (w != null) { //could be already closed
            close(w, file.toString());
        }
    }

    public void closeAll() {
        for (Map.Entry<File, Writer> entry : openFiles.entrySet()) {
            close(entry.getValue(), entry.getKey().toString());
        }
        openFiles.clear();
    }

    private void close(Writer w, String displayName) {
        try {
            w.close();
        } catch (IOException e) {
            throw new RuntimeException("Problems closing " + displayName, e);
        }
    }
}