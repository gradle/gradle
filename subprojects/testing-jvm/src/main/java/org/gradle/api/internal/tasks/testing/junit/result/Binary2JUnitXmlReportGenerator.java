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
import org.gradle.util.Clock;
import org.gradle.internal.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Binary2JUnitXmlReportGenerator {

    private final File testResultsDir;
    private final TestResultsProvider testResultsProvider;
    JUnitXmlResultWriter saxWriter;
    private final static Logger LOG = Logging.getLogger(Binary2JUnitXmlReportGenerator.class);

    public Binary2JUnitXmlReportGenerator(File testResultsDir, TestResultsProvider testResultsProvider, TestOutputAssociation outputAssociation) {
        this.testResultsDir = testResultsDir;
        this.testResultsProvider = testResultsProvider;
        this.saxWriter = new JUnitXmlResultWriter(getHostname(), testResultsProvider, outputAssociation);
    }

    public void generate() {
        Clock clock = new Clock();
        testResultsProvider.visitClasses(new Action<TestClassResult>() {
            public void execute(TestClassResult result) {
                File file = new File(testResultsDir, getReportFileName(result));
                OutputStream output = null;
                try {
                    output = new BufferedOutputStream(new FileOutputStream(file));
                    saxWriter.write(result, output);
                    output.close();
                } catch (Exception e) {
                    throw new GradleException(String.format("Could not write XML test results for %s to file %s.", result.getClassName(), file), e);
                } finally {
                    IOUtils.closeQuietly(output);
                }
            }
        });
        LOG.info("Finished generating test XML results ({}) into: {}", clock.getTime(), testResultsDir);
    }

    private String getReportFileName(TestClassResult result) {
        return "TEST-" + FileUtils.toSafeFileName(result.getClassName()) + ".xml";
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
}