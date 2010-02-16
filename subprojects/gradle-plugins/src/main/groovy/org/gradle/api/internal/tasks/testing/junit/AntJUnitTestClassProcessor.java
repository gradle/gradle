/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTestRunner;
import org.apache.tools.ant.taskdefs.optional.junit.XMLJUnitResultFormatter;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.util.TimeProvider;
import org.gradle.util.TrueTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

public class AntJUnitTestClassProcessor implements TestClassProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntJUnitTestClassProcessor.class);
    private final File testResultsDir;
    private final TimeProvider timeProvider = new TrueTimeProvider();
    private final Field forkedField;
    private JUnitResultFormatter formatter;

    public AntJUnitTestClassProcessor(File testResultsDir) {
        this.testResultsDir = testResultsDir;
        try {
            forkedField = JUnitTestRunner.class.getDeclaredField("forked");
            forkedField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new GradleException(e);
        }
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        formatter = new JUnit4TestListenerFormatter(resultProcessor, timeProvider);
    }

    public void processTestClass(TestClassRunInfo testClass) {
        try {
            LOGGER.debug("Executing test {}", testClass.getTestClassName());

            JUnitTest test = new JUnitTest(testClass.getTestClassName());

            ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
            JUnitTestRunner testRunner = new JUnitTestRunner(test, false, false, false, false, false,
                    applicationClassLoader);
            // Pretend we have been forked - this enables stdout redirection
            forkedField.set(testRunner, true);

            File testResultFile = new File(testResultsDir, "TEST-" + test.getName() + ".xml");
            XMLJUnitResultFormatter xmlFormatter = new XMLJUnitResultFormatter();
            xmlFormatter.setOutput(new BufferedOutputStream(new FileOutputStream(testResultFile)));
            testRunner.addFormatter(xmlFormatter);
            testRunner.addFormatter(formatter);

            testRunner.run();
        } catch (Throwable e) {
            throw new GradleException(String.format("Could not execute test class %s.", testClass.getTestClassName()),
                    e);
        }
    }

    public void endProcessing() {
    }
}
