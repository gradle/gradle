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

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class PersistedTestOutput {

    private final File resultsDir;

    public PersistedTestOutput(File resultsDir) {
        this.resultsDir = resultsDir;
    }

    private File outputsFile(String className, TestOutputEvent.Destination destination) {
        return destination == TestOutputEvent.Destination.StdOut ? standardOutputFile(className) : standardErrorFile(className);
    }

    private File standardErrorFile(String className) {
        return new File(resultsDir, className + ".stderr.bin");
    }

    private File standardOutputFile(String className) {
        return new File(resultsDir, className + ".stdout.bin");
    }

    public class Writer {
        private final CloseableResourceCache<File, Output> outputCache;

        public Writer(int cacheSize) {
            this.outputCache = new CloseableResourceCache<File, Output>(cacheSize, new CloseableResourceCache.ResourceCreator<File, Output>() {
                public Output create(File file) throws IOException {
                    return new Output(new FileOutputStream(file, true));
                }
            });
        }

        public void finishOutputs() {
            outputCache.closeAll();
        }

        public void onOutput(final TestDescriptorInternal testDescriptor, TestOutputEvent.Destination destination, final String message) {
            outputCache.with(outputsFile(testDescriptor.getClassName(), destination), new Action<Output>() {
                public void execute(Output output) {
                    output.writeString(testDescriptor.getName());
                    output.writeString(message);
                }
            });
        }
    }

    public Writer writer() {
        return new Writer(10);
    }

    public class Reader {
        public boolean hasOutput(String className, TestOutputEvent.Destination destination) {
            return outputsFile(className, destination).exists();
        }

        public void readTo(String className, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(className, null, destination, writer);
        }

        public void readTo(String className, String testCaseName, TestOutputEvent.Destination destination, java.io.Writer writer) {
            doRead(className, testCaseName, destination, writer);
        }

        protected void doRead(String className, String targetTestCaseName, TestOutputEvent.Destination destination, java.io.Writer writer) {
            final File file = outputsFile(className, destination);
            if (!file.exists()) {
                return;
            }
            try {
                Input input = new Input(new FileInputStream(file));
                try {
                    while (input.canReadInt()) { // using this to see if we are EOF yet
                        String messageTestCaseName = input.readString();
                        String message = input.readString();

                        if (targetTestCaseName == null || targetTestCaseName.equals(messageTestCaseName)) {
                            writer.write(message);
                        }
                    }
                } finally {
                    input.close();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public Reader reader() {
        return new Reader();
    }
}
