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

package org.gradle.api.tasks.testing;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.testing.junit.report.DefaultTestReport;
import org.gradle.api.internal.tasks.testing.junit.result.BinaryResultBackedTestResultsProvider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Collections;

/**
 * Generates an HTML test report from the results of one or more {@link Test} tasks.
 */
@Incubating
public class TestReport extends DefaultTask {
    private File destinationDir;
    private Iterable<File> testResultDirs = Collections.emptyList();

    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    @InputFiles @SkipWhenEmpty
    public Iterable<File> getTestResultDirs() {
        return testResultDirs;
    }

    public void setTestResultDirs(Iterable<File> testResultDirs) {
        this.testResultDirs = testResultDirs;
    }

    @TaskAction
    void generateReport() {
        BinaryResultBackedTestResultsProvider resultsProvider = new BinaryResultBackedTestResultsProvider(testResultDirs);
        DefaultTestReport testReport = new DefaultTestReport();
        testReport.generateReport(resultsProvider, getDestinationDir());
    }
}
