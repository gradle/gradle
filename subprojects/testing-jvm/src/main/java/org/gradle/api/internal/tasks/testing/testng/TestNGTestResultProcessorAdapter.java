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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.testng.IMethodInstance;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestClass;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.xml.XmlTest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestNGTestResultProcessorAdapter implements ISuiteListener, ITestListener, TestNGConfigurationListener, TestNGClassListener {
    private final TestResultProcessor resultProcessor;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;
    private final Object lock = new Object();
    private final Map<ITestContext, Object> testId = new HashMap<ITestContext, Object>();
    private final Map<ISuite, Object> suiteId = new HashMap<ISuite, Object>();
    private final Map<XmlTest, Object> xmlTestIds = new HashMap<XmlTest, Object>();
    private final Map<ITestClass, Object> testClassId = new HashMap<ITestClass, Object>();
    private final Map<ITestResult, Object> testMethodId = new HashMap<ITestResult, Object>();
    private final Map<ITestNGMethod, Object> testMethodParentId = new HashMap<ITestNGMethod, Object>();
    private final Set<ITestResult> failedConfigurations = new HashSet<ITestResult>();

    public TestNGTestResultProcessorAdapter(TestResultProcessor resultProcessor, IdGenerator<?> idGenerator, Clock clock) {
        this.resultProcessor = resultProcessor;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public void onStart(ISuite suite) {
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            if (suiteId.containsKey(suite)) {
                // Can get duplicate start events
                return;
            }
            Object id = idGenerator.generateId();
            testInternal = new DefaultTestSuiteDescriptor(id, suite.getName());
            suiteId.put(suite, testInternal.getId());
        }
        resultProcessor.started(testInternal, new TestStartEvent(clock.getCurrentTime()));
    }

    @Override
    public void onFinish(ISuite suite) {
        Object id;
        synchronized (lock) {
            id = suiteId.remove(suite);
            if (id == null) {
                // Can get duplicate finish events
                return;
            }
        }

        resultProcessor.completed(id, new TestCompleteEvent(clock.getCurrentTime()));
    }

    @Override
    public void onBeforeClass(ITestClass testClass) {
        TestDescriptorInternal testInternal;
        Object parentId;
        synchronized (lock) {
            testInternal = new DefaultTestClassDescriptor(idGenerator.generateId(), testClass.getName());
            testClassId.put(testClass, testInternal.getId());
            parentId = xmlTestIds.get(testClass.getXmlTest());
        }
        resultProcessor.started(testInternal, new TestStartEvent(clock.getCurrentTime(), parentId));
    }

    @Override
    public void onBeforeClass(ITestClass testClass, IMethodInstance mi) {
    }

    @Override
    public void onAfterClass(ITestClass testClass) {
        Object id;
        synchronized (lock) {
            id = testClassId.remove(testClass);
        }
        resultProcessor.completed(id, new TestCompleteEvent(clock.getCurrentTime()));
    }

    @Override
    public void onAfterClass(ITestClass testClass, IMethodInstance mi) {
    }

    @Override
    public void onStart(ITestContext iTestContext) {
        TestDescriptorInternal testInternal;
        Object parentId;
        synchronized (lock) {
            Object id = idGenerator.generateId();
            testInternal = new DefaultTestSuiteDescriptor(id, iTestContext.getName());
            parentId = suiteId.get(iTestContext.getSuite());
            xmlTestIds.put(iTestContext.getCurrentXmlTest(), id);
            testId.put(iTestContext, testInternal.getId());
            for (ITestNGMethod method : iTestContext.getAllTestMethods()) {
                testMethodParentId.put(method, testInternal.getId());
            }
        }
        resultProcessor.started(testInternal, new TestStartEvent(iTestContext.getStartDate().getTime(), parentId));
    }

    @Override
    public void onFinish(ITestContext iTestContext) {
        Object id;
        synchronized (lock) {
            id = testId.remove(iTestContext);
            xmlTestIds.remove(iTestContext.getCurrentXmlTest());
            for (ITestNGMethod method : iTestContext.getAllTestMethods()) {
                testMethodParentId.remove(method);
            }
        }
        resultProcessor.completed(id, new TestCompleteEvent(iTestContext.getEndDate().getTime()));
    }

    @Override
    public void onTestStart(ITestResult iTestResult) {
        TestDescriptorInternal testInternal;
        Object parentId;
        synchronized (lock) {
            String name = calculateTestCaseName(iTestResult);
            testInternal = new DefaultTestMethodDescriptor(idGenerator.generateId(), iTestResult.getTestClass().getName(), name);
            Object oldTestId = testMethodId.put(iTestResult, testInternal.getId());
            assert oldTestId == null : "Apparently some other test has started but it hasn't finished. "
                + "Expect the resultProcessor to break. "
                + "Don't expect to see this assertion stack trace due to the current architecture";

            parentId = testMethodParentId.get(iTestResult.getMethod());
            assert parentId != null;
        }
        resultProcessor.started(testInternal, new TestStartEvent(iTestResult.getStartMillis(), parentId));

        if (iTestResult.getThrowable() instanceof UnrepresentableParameterException) {
            throw (UnrepresentableParameterException) iTestResult.getThrowable();
        }
    }

    private String calculateTestCaseName(ITestResult iTestResult) {
        Object[] parameters = iTestResult.getParameters();
        String name = iTestResult.getName();
        if (parameters != null && parameters.length > 0) {
            StringBuilder builder = new StringBuilder(name).
                append("[").
                append(iTestResult.getMethod().getCurrentInvocationCount()).
                append("]");

            StringBuilder paramsListBuilder = new StringBuilder("(");
            int i = 0;
            for (Object parameter : parameters) {
                if (parameter == null) {
                    paramsListBuilder.append("null");
                } else {
                    try {
                        paramsListBuilder.append(parameter.toString());
                    } catch (Exception e) {
                        // This may be thrown by the caller of this method at a later time
                        iTestResult.setThrowable(new UnrepresentableParameterException(iTestResult, i, e));
                        return builder.toString();
                    }
                }
                if (++i < parameters.length) {
                    paramsListBuilder.append(", ");
                }
            }
            paramsListBuilder.append(")");
            return builder.append(paramsListBuilder.toString()).toString();
        } else {
            return name;
        }
    }

    @Override
    public void onTestSuccess(ITestResult iTestResult) {
        onTestFinished(iTestResult, TestResult.ResultType.SUCCESS);
    }

    @Override
    public void onTestFailure(ITestResult iTestResult) {
        onTestFinished(iTestResult, TestResult.ResultType.FAILURE);
    }

    @Override
    public void onTestSkipped(ITestResult iTestResult) {
        onTestFinished(iTestResult, TestResult.ResultType.SKIPPED);
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult iTestResult) {
        onTestFinished(iTestResult, TestResult.ResultType.SUCCESS);
    }

    private void onTestFinished(ITestResult iTestResult, TestResult.ResultType resultType) {
        Object testId;
        TestStartEvent startEvent = null;
        synchronized (lock) {
            testId = testMethodId.remove(iTestResult);
            if (testId == null) {
                // This can happen when a method fails which this method depends on
                testId = idGenerator.generateId();
                Object parentId = testMethodParentId.get(iTestResult.getMethod());
                startEvent = new TestStartEvent(iTestResult.getStartMillis(), parentId);
            }
        }
        if (startEvent != null) {
            // Synthesize a start event
            resultProcessor.started(new DefaultTestMethodDescriptor(testId, iTestResult.getTestClass().getName(), iTestResult.getName()), startEvent);
        }
        if (resultType == TestResult.ResultType.FAILURE) {
            resultProcessor.failure(testId, iTestResult.getThrowable());
        }
        resultProcessor.completed(testId, new TestCompleteEvent(iTestResult.getEndMillis(), resultType));
    }

    @Override
    public void onConfigurationSuccess(ITestResult testResult) {
    }

    @Override
    public void onConfigurationSkip(ITestResult testResult) {
    }

    @Override
    public void onConfigurationFailure(ITestResult testResult) {
        synchronized (lock) {
            if (!failedConfigurations.add(testResult)) {
                // workaround for bug in TestNG 6.2 (apparently fixed in some 6.3.x): listener is notified twice per event
                return;
            }
        }
        // Synthesise a test for the broken configuration method
        TestDescriptorInternal test = new DefaultTestMethodDescriptor(idGenerator.generateId(),
            testResult.getMethod().getTestClass().getName(), testResult.getMethod().getMethodName());
        resultProcessor.started(test, new TestStartEvent(testResult.getStartMillis()));
        resultProcessor.failure(test.getId(), testResult.getThrowable());
        resultProcessor.completed(test.getId(), new TestCompleteEvent(testResult.getEndMillis(), TestResult.ResultType.FAILURE));
    }

    @Override
    public void beforeConfiguration(ITestResult tr) {
    }
}
