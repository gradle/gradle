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
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.*;

/**
 * Assembles test results. Keeps a copy of the results in memory to provide them later and spools test output to file.
 *
 * by Szczepan Faber, created at: 11/13/12
 */
public class TestOutputSerializer {
    private final File resultsDir;
    private final CachingFileWriter cachingFileWriter;

    public TestOutputSerializer(File resultsDir) {
        //TODO SF calculate number of open files based on parallel forks
        this(resultsDir, new CachingFileWriter(10));
    }

    private TestOutputSerializer(File resultsDir, CachingFileWriter cachingFileWriter) {
        this.resultsDir = resultsDir;
        this.cachingFileWriter = cachingFileWriter;
    }

    private File outputsFile(String className, TestOutputEvent.Destination destination) {
        return destination == TestOutputEvent.Destination.StdOut ? standardOutputFile(className) : standardErrorFile(className);
    }

    private File standardErrorFile(String className) {
        return new File(resultsDir, className + ".stderr");
    }

    private File standardOutputFile(String className) {
        return new File(resultsDir, className + ".stdout");
    }

    public boolean hasOutput(String className, TestOutputEvent.Destination destination){
        return outputsFile(className, destination).exists();
    }

    public void writeOutputs(String className, TestOutputEvent.Destination destination, Writer writer) {
        final File file = outputsFile(className, destination);
        if (!file.exists()) {
            return;
        }
        try {
            Reader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)), "UTF-8");
            try {
                char[] buffer = new char[2048];
                while (true) {
                    int read = reader.read(buffer);
                    if (read < 0) {
                        return;
                    }
                    writer.write(buffer, 0, read);
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void finishOutputs() {
        cachingFileWriter.closeAll();
    }

    public void onOutput(String className, TestOutputEvent.Destination destination, String message) {
        cachingFileWriter.write(outputsFile(className, destination), message);
    }
}