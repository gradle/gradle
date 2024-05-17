/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.TestSuiteExecutionException;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.AllTests;

import java.util.List;

public class IgnoredTestDescriptorProvider {
    public static List<Description> getAllDescriptions(Description description, String className) {
        try {
            final Class<?> testClass = description.getClass().getClassLoader().loadClass(className);
            Runner runner = getRunner(testClass);
            final Description runnerDescription = runner.getDescription();
            return runnerDescription.getChildren();
        } catch (Throwable throwable) {
            throw new TestSuiteExecutionException(String.format("Unable to process Ignored class %s.", className), throwable);
        }
    }

    private static Runner getRunner(Class<?> testClass) throws Throwable {
        try {
            final AllExceptIgnoredTestRunnerBuilder allExceptIgnoredTestRunnerBuilder = new AllExceptIgnoredTestRunnerBuilder();
            Runner runner = allExceptIgnoredTestRunnerBuilder.runnerForClass(testClass);
            if (runner == null) {
                // Fall back to default runner
                runner = Request.aClass(testClass).getRunner();
            }
            return runner;
        } catch (NoClassDefFoundError e) {
            return getRunnerLegacy(testClass);
        } catch (NoSuchMethodError e) {
            return getRunnerLegacy(testClass);
        }
    }

    /**
     * Prior to JUnit 4.5, the {@code RunnerBuilder} class did not exist, and so we cannot use the {@link AllExceptIgnoredTestRunnerBuilder}.
     * Therefore, we manually construct the runner ourselves the same way it was done in 4.4.
     *
     * @see <a href="https://github.com/junit-team/junit4/commit/67e3edf20613b1278f4be05353b31b5129e21882">The relevant commit</a>
     */
    @SuppressWarnings("deprecation")
    static Runner getRunnerLegacy(Class<?> testClass) throws Throwable {
        RunWith annotation = testClass.getAnnotation(RunWith.class);
        if (annotation != null) {
            Class<? extends Runner> runnerClass = annotation.value();
            try {
                return runnerClass.getConstructor(Class.class).newInstance(testClass);
            } catch (NoSuchMethodException e) {
                String simpleName = runnerClass.getSimpleName();
                throw new org.junit.internal.runners.InitializationError("Custom runner class " + simpleName +
                    " should have a public constructor with signature " + simpleName + "(Class testClass)");
            }
        } else if (hasSuiteMethod(testClass)) {
            return new AllTests(testClass);
        } else if (junit.framework.TestCase.class.isAssignableFrom(testClass)) {
            return new JUnit38ClassRunner(testClass);
        } else {
            return new org.junit.internal.runners.JUnit4ClassRunner(testClass);
        }
    }

    private static boolean hasSuiteMethod(Class<?> testClass) {
        try {
            testClass.getMethod("suite");
        } catch (NoSuchMethodException e) {
            return false;
        }
        return true;
    }
}
