/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.external.junit;

import junit.framework.AssertionFailedError;
import junit.framework.TestFailure;
import junit.framework.TestListener;
import org.gradle.api.logging.DefaultStandardOutputCapture;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputCapture;
import org.gradle.api.testing.fabric.AbstractTestProcessor;
import org.gradle.api.testing.fabric.TestClassProcessResult;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.fabric.TestProcessResultFactory;
import org.gradle.initialization.DefaultLoggingConfigurer;
import org.gradle.initialization.LoggingConfigurer;
import org.gradle.util.ContextClassLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestProcessor extends AbstractTestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JUnitTestProcessor.class);

    private JUnitTestClassHandlerFactory testClassHandlerFactory;

    private final List<TestListener> testListeners;

    public JUnitTestProcessor(ClassLoader sandboxClassLoader, TestProcessResultFactory testProcessResultFactory) {
        super(sandboxClassLoader, testProcessResultFactory);

        testClassHandlerFactory = new JUnitTestClassHandlerFactory();
        this.testListeners = new ArrayList<TestListener>();
    }

    public void addTestListener(junit.framework.TestListener testListener) {
        testListeners.add(new JUnitTestListenerWrapper(testListener));
    }

    public TestClassProcessResult process(final TestClassRunInfo testClassRunInfo) {
        final DefaultLoggingConfigurer loggingConfigurer = new DefaultLoggingConfigurer();
        loggingConfigurer.configure(LogLevel.LIFECYCLE);

        final DefaultStandardOutputCapture stdOutputCapture = new DefaultStandardOutputCapture(true, LogLevel.DEBUG);

        ContextClassLoaderRunnable contextClassLoaderRunnable = new ContextClassLoaderRunnable(testClassRunInfo, loggingConfigurer, stdOutputCapture, testClassHandlerFactory);

        ContextClassLoaderUtil.runWith(sandboxClassLoader, contextClassLoaderRunnable);

        return contextClassLoaderRunnable.getClassProcessResult();
    }

    private class ContextClassLoaderRunnable implements Runnable {
        private final TestClassRunInfo testClassRunInfo;
        private final LoggingConfigurer loggingConfigurer;
        private final StandardOutputCapture stdOutputCapture;
        private TestClassProcessResult classProcessResult;
        private final JUnitTestClassHandlerFactory testClassHandlerFactory;

        public ContextClassLoaderRunnable(TestClassRunInfo testClassRunInfo, LoggingConfigurer loggingConfigurer, StandardOutputCapture stdOutputCapture, JUnitTestClassHandlerFactory testClassHandlerFactory) {
            this.testClassRunInfo = testClassRunInfo;
            this.loggingConfigurer = loggingConfigurer;
            this.stdOutputCapture = stdOutputCapture;
            this.testClassHandlerFactory = testClassHandlerFactory;
        }

        public void run() {
            final String testClassName = testClassRunInfo.getTestClassName();

            classProcessResult = testProcessResultFactory.createEmptyClassResult(testClassRunInfo);

            testListeners.add(new JUnitTestListenerWrapper(new JUnitClassProcessResultAdapaterListener(classProcessResult)));

            junit.framework.TestResult testResult = new junit.framework.TestResult();
            for (final junit.framework.TestListener testListener : testListeners) {
                testResult.addListener(testListener);
            }

            try {
                final Class testClass = Class.forName(testClassName, true, sandboxClassLoader);

                final JUnitTestClassHandler testClassHandler = testClassHandlerFactory.createTestClassHandler(testClass);
                final junit.framework.Test suite = testClassHandler.getSuite();

                stdOutputCapture.start();
                suite.run(testResult);
                stdOutputCapture.stop();

//                JUnitTestResultTransformer junitTestResultTransformer = new JUnitTestResultTransformer(suite, testResult);

//                junitTestResultTransformer.transform(classProcessResult);

                // count run, success, failures, errors, ignored...
                int failures = 0;
                int errors = 0;
                Enumeration e = testResult.failures();
                while (e.hasMoreElements()) {
                    e.nextElement();
                    failures++;
                }
                e = testResult.errors();
                while (e.hasMoreElements()) {
                    Throwable t = ((TestFailure) e.nextElement()).thrownException();
                    if (t instanceof AssertionFailedError
                            || t.getClass().getName().equals("java.lang.AssertionError")) {
                        failures++;
                    } else {
                        errors++;
                    }
                }

                final int errorCount = errors;
                final int failureCount = failures;
                final int runCount = testResult.runCount();
                final int successCount = runCount - (errorCount + failureCount);

                if (errorCount > 0 || failureCount > 0) {
                    logger.warn(testClassName + "[run #: " + runCount + ", success #: " + successCount + ", failure #: " + failureCount + ", error #: " + errorCount + "]");
                    if (failureCount > 0) {
                        final Enumeration failuresEnumeration = testResult.failures();
                        while (failuresEnumeration.hasMoreElements()) {
                            final junit.framework.TestFailure testFailure = (junit.framework.TestFailure) failuresEnumeration.nextElement();

                            Throwable t = testFailure.thrownException();
                            logger.warn("\t {} failed because of [FAILURE] {}", testFailure.failedTest(), testFailure.exceptionMessage());
                            logger.warn("\t\t", t);
                        }
                    }
                    if (errorCount > 0) {
                        final Enumeration errorsEnumeration = testResult.errors();
                        while (errorsEnumeration.hasMoreElements()) {
                            final junit.framework.TestFailure testError = (junit.framework.TestFailure) errorsEnumeration.nextElement();

                            Throwable t = testError.thrownException();
                            if (t instanceof AssertionFailedError
                                    || t.getClass().getName().equals("java.lang.AssertionError")) {
                                logger.warn("\t {} failed because of [FAILURE] {}", testError.failedTest(), testError.exceptionMessage());
                                logger.warn("\t\t", t);
                            } else {
                                logger.warn("\t {} failed because of [ERROR] {}", testError.failedTest(), testError.exceptionMessage());
                                logger.warn("\t\t", t);
                            }
                        }
                    }
                } else
                    logger.debug(testClassName + "[run #: " + runCount + ", success #: " + successCount + ", failure #: " + failureCount + ", error #: " + errorCount + "]");
            }
            catch (Throwable t) {
                classProcessResult.setProcessorErrorReason(t);
            }
        }

        public TestClassProcessResult getClassProcessResult() {
            return classProcessResult;
        }
    }
}
