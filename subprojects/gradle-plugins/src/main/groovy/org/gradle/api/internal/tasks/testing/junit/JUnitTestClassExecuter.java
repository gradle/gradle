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

import junit.framework.*;
import org.junit.runner.Describable;
import org.junit.runner.Description;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JUnitTestClassExecuter {
    private final ClassLoader applicationClassLoader;
    private final JUnitTestResultProcessorAdapter listener;

    public JUnitTestClassExecuter(ClassLoader applicationClassLoader, JUnitTestResultProcessorAdapter listener) {
        this.applicationClassLoader = applicationClassLoader;
        this.listener = listener;
    }

    public void execute(String testClassName) {
        listener.startTestSuite(testClassName);

        Test adapter = createTest(testClassName);
        TestResult result = new TestResult();
        result.addListener(listener);
        adapter.run(result);

        listener.endTestSuite();
    }

    private Test createTest(String testClassName) {
        Class<?> testClass;
        try {
            testClass = Class.forName(testClassName, true, applicationClassLoader);
        } catch (Throwable e) {
            return new BrokenTest(
                    Description.createSuiteDescription(String.format("initializationError(%s)", testClassName)), e);
        }

        // Look for a static suite method
        try {
            Method suiteMethod = testClass.getMethod("suite", new Class[0]);
            return (Test) suiteMethod.invoke(null);
        } catch (NoSuchMethodException e) {
            // Ignore
        } catch (InvocationTargetException e) {
            return new BrokenTest(Description.createTestDescription(testClass, "suite"), e.getCause());
        } catch (IllegalAccessException e) {
            return new BrokenTest(Description.createTestDescription(testClass, "suite"), e);
        }

        return new JUnit4TestAdapter(testClass);
    }

    private static class BrokenTest implements Test, Describable {
        private final Throwable failure;
        private final Description description;

        public BrokenTest(Description description, Throwable failure) {
            this.failure = failure;
            this.description = description;
        }

        public Description getDescription() {
            return description;
        }

        public int countTestCases() {
            return 1;
        }

        public void run(TestResult result) {
            result.startTest(this);
            result.addError(this, failure);
            result.endTest(this);
        }
    }
}
