/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.io.CharStreams;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class TestOutputStore {

    private final File resultsDir;
    private final Charset messageStorageCharset;

    public TestOutputStore(File resultsDir) {
        this.resultsDir = resultsDir;
        this.messageStorageCharset = StandardCharsets.UTF_8;
    }

    File getOutputsFile() {
        return new File(resultsDir, "output.zip");
    }

    private static String formatOutputFileName(long id, TestOutputEvent.Destination destination) {
        return String.format("%s/%s.bin", id, destination == TestOutputEvent.Destination.StdOut ? "stdout" : "stderr");
    }

    public class Writer implements Closeable {
        private final ZipOutputStream output;

        public Writer() {
            try {
                output = new ZipOutputStream(Files.newOutputStream(getOutputsFile().toPath()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            output.close();
        }

        public void onOutput(long testId, TestOutputEvent outputEvent) {
            try {
                output.putNextEntry(new ZipEntry(formatOutputFileName(testId, outputEvent.getDestination())));
                OutputStreamWriter writer = new OutputStreamWriter(output, messageStorageCharset);
                writer.write(outputEvent.getMessage());
                // Specifically not closing to keep `output` open.
                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public Writer writer() {
        return new Writer();
    }

    public class Reader implements Closeable {
        private final ZipFile dataFile;

        public Reader() {
            if (!getOutputsFile().isFile()) {
                dataFile = null;
            } else {
                try {
                    dataFile = new ZipFile(getOutputsFile());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (dataFile != null) {
                dataFile.close();
            }
        }

        public boolean hasOutput(long testId, TestOutputEvent.Destination destination) {
            if (dataFile == null) {
                return false;
            }

            return dataFile.getEntry(formatOutputFileName(testId, destination)) != null;
        }

        public void copyTestOutput(long testId, TestOutputEvent.Destination destination, java.io.Writer writer) {
            if (dataFile == null) {
                return;
            }

            ZipEntry entry = dataFile.getEntry(formatOutputFileName(testId, destination));
            if (entry == null) {
                return;
            }
            try (InputStream inputStream = dataFile.getInputStream(entry)) {
                CharStreams.copy(new InputStreamReader(inputStream, messageStorageCharset), writer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    // IMPORTANT: return must be closed when done with.
    public Reader reader() {
        return new Reader();
    }
}
