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

package org.gradle.internal;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

/**
 * Various utilities for dealing with IO actions.
 */
public abstract class IoActions {

    /**
     * Gives a writer for the given file/encoding to the given write action, managing the streams.
     *
     * @param output The file to write to
     * @param encoding The character encoding to write with
     * @param action The action to write the actual content
     */
    public static void writeTextFile(File output, String encoding, Action<? super BufferedWriter> action) {
        createTextFileWriteAction(output, encoding).execute(action);
    }

    /**
     * Gives a writer (with the default file encoding) to the given write action, managing the streams.
     *
     * @param output The file to write to
     * @param action The action to write the actual content
     */
    public static void writeTextFile(File output, Action<? super BufferedWriter> action) {
        writeTextFile(output, Charset.defaultCharset().name(), action);
    }

    /**
     * Creates an action that itself takes an action that will perform that actual writing to the file.
     *
     * All IO is deferred until the execution of the returned action.
     *
     * @param output The file to write to
     * @param encoding The character encoding to write with
     * @return An action that receives an action that performs the actual writing
     */
    public static Action<Action<? super BufferedWriter>> createTextFileWriteAction(File output, String encoding) {
        return new TextFileWriterIoAction(output, encoding);
    }

    /**
     * Performs the given action against the given resource, then closes the resource. Ignores failure to close the resource when
     * the action throws an exception.
     */
    public static <T extends Closeable> void withResource(T resource, Action<? super T> action) {
        try {
            action.execute(resource);
        } catch (Throwable t) {
            closeQuietly(resource);
            throw UncheckedException.throwAsUncheckedException(t);
        }
        uncheckedClose(resource);
    }

    public static <T extends Closeable, R> R withResource(T resource, Transformer<R, ? super T> action) {
        R result;
        try {
            result = action.transform(resource);
        } catch (Throwable t) {
            closeQuietly(resource);
            throw UncheckedException.throwAsUncheckedException(t);
        }
        uncheckedClose(resource);
        return result;
    }

    /**
     * Closes the given resource rethrowing any {@link IOException} as a {@link UncheckedIOException}.
     *
     * @param resource The resource to be closed
     */
    public static void uncheckedClose(@Nullable Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Closes the given resource silently ignoring any {@link IOException}.
     *
     * @param resource The resource to be closed
     */
    public static void closeQuietly(@Nullable Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (IOException e) {
            // Ignored
        }
    }

    private static class TextFileWriterIoAction implements Action<Action<? super BufferedWriter>> {
        private final File file;
        private final String encoding;

        private TextFileWriterIoAction(File file, String encoding) {
            this.file = file;
            this.encoding = encoding;
        }

        @Override
        public void execute(Action<? super BufferedWriter> action) {
            try {
                File parentFile = file.getParentFile();
                if (parentFile != null) {
                    if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
                        throw new IOException(String.format("Unable to create directory '%s'", parentFile));
                    }
                }
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
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
