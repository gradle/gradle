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

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.util.IdGenerator;
import org.gradle.util.TimeProvider;
import org.gradle.util.TrueTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class AntJUnitTestClassProcessor implements TestClassProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntJUnitTestClassProcessor.class);
    private final File testResultsDir;
    private final IdGenerator<?> idGenerator;
    private final TimeProvider timeProvider = new TrueTimeProvider();
    private JUnitTestClassExecuter executer;

    public AntJUnitTestClassProcessor(File testResultsDir, IdGenerator<?> idGenerator) {
        this.testResultsDir = testResultsDir;
        this.idGenerator = idGenerator;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
        ListenerBroadcast<TestResultProcessor> processors = new ListenerBroadcast<TestResultProcessor>(
                TestResultProcessor.class);
        processors.add(new JUnitXmlReportGenerator(testResultsDir));
        processors.add(resultProcessor);
        TestResultProcessor resultProcessorChain = new CaptureTestOutputTestResultProcessor(processors.getSource());
        JUnitTestResultProcessorAdapter listener = new JUnit4TestResultProcessorAdapter(resultProcessorChain,
                timeProvider, idGenerator);
        executer = new JUnitTestClassExecuter(applicationClassLoader, listener);
    }

    public void processTestClass(TestClassRunInfo testClass) {
        try {
            LOGGER.debug("Executing test {}", testClass.getTestClassName());
            executer.execute(testClass.getTestClassName());
        } catch (Throwable e) {
            throw new GradleException(String.format("Could not execute test class '%s'.", testClass.getTestClassName()),
                    e);
        }
    }

    public void endProcessing() {
    }
}
