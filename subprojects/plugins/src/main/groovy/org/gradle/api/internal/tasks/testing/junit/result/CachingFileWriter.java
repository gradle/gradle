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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.internal.CompositeStoppable.stoppable;

/**
 * by Szczepan Faber, created at: 11/19/12
 */
public class CachingFileWriter {

    private final static Logger LOG = Logging.getLogger(CachingFileWriter.class);

    final LinkedHashMap<File, Writer> openFiles = new LinkedHashMap<File, Writer>();
    private final int openFilesCount;

    public CachingFileWriter(int openFilesCount) {
        this.openFilesCount = openFilesCount;
    }

    public void write(File file, String text) {
        Writer out;
        try {
            try {
                if (openFiles.containsKey(file)) {
                    out = openFiles.get(file);
                } else {
                    out = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(file, true)), "UTF-8");
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
                throw new UncheckedIOException("Problems writing to file: " + file, e);
            }
        } catch (UncheckedIOException e) {
            cleanUpQuietly();
            throw e;
        }
    }

    public void closeAll() {
        try {
            for (Map.Entry<File, Writer> entry : openFiles.entrySet()) {
                close(entry.getValue(), entry.getKey().toString());
            }
        } catch (UncheckedIOException e) {
            cleanUpQuietly();
            throw e;
        } finally {
            openFiles.clear();
        }
    }

    private void cleanUpQuietly() {
        try {
            stoppable(openFiles.values()).stop();
        } catch (Exception e) {
            LOG.debug("Problems closing files", e);
        } finally {
            openFiles.clear();
        }
    }

    private void close(Closeable c, String displayName) {
        try {
            c.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Problems closing file: " + displayName, e);
        }
    }
}