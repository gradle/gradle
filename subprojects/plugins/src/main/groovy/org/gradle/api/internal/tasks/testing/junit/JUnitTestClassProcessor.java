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

import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.processors.CaptureTestOutputTestResultProcessor;
import org.gradle.api.internal.tasks.testing.results.AttachParentTestResultProcessor;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.logging.StandardOutputRedirector;
import org.gradle.util.IdGenerator;
import org.gradle.util.TimeProvider;
import org.gradle.util.TrueTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class JUnitTestClassProcessor implements TestClassProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(JUnitTestClassProcessor.class);
    private final File testResultsDir;
    private final IdGenerator<?> idGenerator;
    private final StandardOutputRedirector outputRedirector;
    private final TimeProvider timeProvider = new TrueTimeProvider();
    private JUnitTestClassExecuter executer;

    public JUnitTestClassProcessor(File testResultsDir, IdGenerator<?> idGenerator,
                                   StandardOutputRedirector standardOutputRedirector) {
        this.testResultsDir = testResultsDir;
        this.idGenerator = idGenerator;
        this.outputRedirector = standardOutputRedirector;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
        ListenerBroadcast<TestResultProcessor> processors = new ListenerBroadcast<TestResultProcessor>(
                TestResultProcessor.class);
        processors.add(new JUnitXmlReportGenerator(testResultsDir));
        processors.add(resultProcessor);
        TestResultProcessor resultProcessorChain = new AttachParentTestResultProcessor(new CaptureTestOutputTestResultProcessor(processors.getSource(), outputRedirector));
        JUnitTestResultProcessorAdapter listener = new JUnitTestResultProcessorAdapter(resultProcessorChain,
                timeProvider, idGenerator);
        executer = new JUnitTestClassExecuter(applicationClassLoader, listener, resultProcessorChain, idGenerator, timeProvider);
    }

    public void processTestClass(TestClassRunInfo testClass) {
        LOGGER.debug("Executing test {}", testClass.getTestClassName());
        executer.execute(testClass.getTestClassName());
    }

    public void stop() {
    }
}
