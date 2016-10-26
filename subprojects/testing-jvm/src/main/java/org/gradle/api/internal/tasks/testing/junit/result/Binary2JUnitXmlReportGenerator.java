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
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.FileUtils;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;

import java.io.File;
import java.io.FileOutputStream;

public class Binary2JUnitXmlReportGenerator {

    private final File testResultsDir;
    private final TestResultsProvider testResultsProvider;
    private JUnitXmlResultWriter xmlWriter;
    private final BuildOperationProcessor buildOperationProcessor;
    private final static Logger LOG = Logging.getLogger(Binary2JUnitXmlReportGenerator.class);

    public Binary2JUnitXmlReportGenerator(File testResultsDir, TestResultsProvider testResultsProvider, TestOutputAssociation outputAssociation, BuildOperationProcessor buildOperationProcessor, String hostName) {
        this.testResultsDir = testResultsDir;
        this.testResultsProvider = testResultsProvider;
        this.xmlWriter = new JUnitXmlResultWriter(hostName, testResultsProvider, outputAssociation);
        this.buildOperationProcessor = buildOperationProcessor;
    }

    public void generate() {
        Timer clock = Timers.startTimer();

        buildOperationProcessor.run(new Action<BuildOperationQueue<JUnitXmlReportFileGenerator>>() {
            @Override
            public void execute(final BuildOperationQueue<JUnitXmlReportFileGenerator> queue) {
                testResultsProvider.visitClasses(new Action<TestClassResult>() {
                    public void execute(final TestClassResult result) {
                        final File reportFile = new File(testResultsDir, getReportFileName(result));
                        queue.add(new JUnitXmlReportFileGenerator(result, reportFile, xmlWriter));
                    }
                });
            }
        });

        LOG.info("Finished generating test XML results ({}) into: {}", clock.getElapsed(), testResultsDir);
    }

    private String getReportFileName(TestClassResult result) {
        return "TEST-" + FileUtils.toSafeFileName(result.getClassName()) + ".xml";
    }

    private static class JUnitXmlReportFileGenerator implements RunnableBuildOperation {
        private final TestClassResult result;
        private final File reportFile;
        private final JUnitXmlResultWriter xmlWriter;

        public JUnitXmlReportFileGenerator(TestClassResult result, File reportFile, JUnitXmlResultWriter xmlWriter) {
            this.result = result;
            this.reportFile = reportFile;
            this.xmlWriter = xmlWriter;
        }

        @Override
        public String getDescription() {
            return "generating junit xml test report for ".concat(result.getClassName());
        }

        @Override
        public void run() {
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(reportFile);
                xmlWriter.write(result, output);
                output.close();
            } catch (Exception e) {
                throw new GradleException(String.format("Could not write XML test results for %s to file %s.", result.getClassName(), reportFile), e);
            } finally {
                IOUtils.closeQuietly(output);
            }
        }
    }
}
