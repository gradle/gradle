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
import org.gradle.api.tasks.testing.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 11/13/12
 */
public class TestReportDataCollector implements TestListener, TestOutputListener, TestResultsProvider {

    private final Map<String, TestClassResult> results = new HashMap<String, TestClassResult>();
    private final File resultsDir;
    CachingFileWriter cachingFileWriter = new CachingFileWriter(10); //TODO SF calculate based on parallel forks

    public TestReportDataCollector(File resultsDir) {
        this.resultsDir = resultsDir;
        if (!resultsDir.isDirectory()) {
            throw new IllegalArgumentException("Directory [" + resultsDir + "] for binary test results does not exist or it is not a valid folder.");
        }
        if (resultsDir.list().length > 0) {
            throw new IllegalArgumentException("Directory [" + resultsDir + "] for binary test results must be empty!");
        }
    }

    public void beforeSuite(TestDescriptor suite) {
    }

    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (suite.getParent() == null) {
            cachingFileWriter.closeAll();
        }
    }

    public void beforeTest(TestDescriptor testDescriptor) {
    }

    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        if (!testDescriptor.isComposite()) {
            String className = testDescriptor.getClassName();
            TestMethodResult methodResult = new TestMethodResult(testDescriptor.getName(), result);
            TestClassResult classResult = results.get(className);
            if (classResult == null) {
                classResult = new TestClassResult(result.getStartTime());
                results.put(className, classResult);
            }
            classResult.add(methodResult);
        }
    }

    public void onOutput(TestDescriptor testDescriptor, TestOutputEvent outputEvent) {
        String className = testDescriptor.getClassName();
        if (className == null) {
            //this means that we receive an output before even starting any class (or too late).
            //we don't have a place for such output in any of the reports so skipping.
            return;
        }
        cachingFileWriter.write(outputsFile(className, outputEvent.getDestination()), outputEvent.getMessage());
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

    public Map<String, TestClassResult> getResults() {
        return results;
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
}