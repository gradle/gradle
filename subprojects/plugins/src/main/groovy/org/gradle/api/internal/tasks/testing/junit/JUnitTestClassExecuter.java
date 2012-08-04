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

import org.gradle.internal.concurrent.ThreadSafe;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

public class JUnitTestClassExecuter {
    private final ClassLoader applicationClassLoader;
    private final RunListener listener;
    private final TestClassExecutionListener executionListener;

    public JUnitTestClassExecuter(ClassLoader applicationClassLoader, RunListener listener, TestClassExecutionListener executionListener) {
        assert executionListener instanceof ThreadSafe;
        this.applicationClassLoader = applicationClassLoader;
        this.listener = listener;
        this.executionListener = executionListener;
    }

    public void execute(String testClassName) {
        executionListener.testClassStarted(testClassName);

        Throwable failure = null;
        try {
            runTestClass(testClassName);
        } catch (Throwable throwable) {
            failure = throwable;
        }

        executionListener.testClassFinished(failure);
    }

    private void runTestClass(String testClassName) throws ClassNotFoundException {
        Class<?> testClass = Class.forName(testClassName, true, applicationClassLoader);
        Runner runner = Request.aClass(testClass).getRunner();
        RunNotifier notifier = new RunNotifier();
        notifier.addListener(listener);
        runner.run(notifier);
    }
}
