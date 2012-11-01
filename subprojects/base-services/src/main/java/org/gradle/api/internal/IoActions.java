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

package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;

import java.io.*;

/**
 * Various utilities for dealing with IO actions.
 */
public abstract class IoActions {

    public static  Action<? super Action<? super Writer>> createFileWriteAction(File output, String encoding) {
        return new FileWriterIoAction(output, encoding);
    }

    private static class FileWriterIoAction implements Action<Action<? super Writer>> {
        private final File file;
        private final String encoding;

        private FileWriterIoAction(File file, String encoding) {
            this.file = file;
            this.encoding = encoding;
        }

        public void execute(Action<? super Writer> action) {
            try {
                File parentFile = file.getParentFile();
                if (parentFile != null) {
                    if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
                        throw new IOException(String.format("Unable to create directory '%s'", parentFile));
                    }
                }
                Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
                try {
                    action.execute(writer);
                } finally {
                    writer.close();
                }
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Could not write to file '%s'.", file), e);
            }
        }
    }

}
