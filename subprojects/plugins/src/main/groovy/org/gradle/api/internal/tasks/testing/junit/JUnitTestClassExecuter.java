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

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.util.IdGenerator;
import org.gradle.util.TimeProvider;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

public class JUnitTestClassExecuter {
    private final ClassLoader applicationClassLoader;
    private final RunListener listener;
    private final TestResultProcessor resultProcessor;
    private final IdGenerator<?> idGenerator;
    private final TimeProvider timeProvider;

    public JUnitTestClassExecuter(ClassLoader applicationClassLoader, RunListener listener, TestResultProcessor resultProcessor, IdGenerator<?> idGenerator, TimeProvider timeProvider) {
        this.applicationClassLoader = applicationClassLoader;
        this.listener = listener;
        this.resultProcessor = resultProcessor;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public void execute(String testClassName) {
        TestDescriptorInternal testInternal = new DefaultTestClassDescriptor(idGenerator.generateId(), testClassName);
        resultProcessor.started(testInternal, new TestStartEvent(timeProvider.getCurrentTime()));

        Runner runner = createTest(testClassName);
        RunNotifier notifier = new RunNotifier();
        notifier.addListener(listener);
        runner.run(notifier);

        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(timeProvider.getCurrentTime()));
    }

    private Runner createTest(String testClassName) {
        try {
            Class<?> testClass = Class.forName(testClassName, true, applicationClassLoader);
            return Request.aClass(testClass).getRunner();
        } catch (Throwable e) {
            return new BrokenTest(Description.createSuiteDescription(String.format("initializationError(%s)", testClassName)), e);
        }
    }

    private static class BrokenTest extends Runner {
        private final Throwable failure;
        private final Description description;

        public BrokenTest(Description description, Throwable failure) {
            this.failure = failure;
            this.description = description;
        }

        public Description getDescription() {
            return description;
        }

        @Override
        public void run(RunNotifier notifier) {
            notifier.fireTestStarted(description);
            notifier.fireTestFailure(new Failure(description, failure));
            notifier.fireTestFinished(description);
        }
    }
}
