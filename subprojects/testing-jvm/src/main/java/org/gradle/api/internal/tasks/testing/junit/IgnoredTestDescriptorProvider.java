/*
 * Copyright 2013 the original author or authors.
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
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;

import java.util.List;

public class IgnoredTestDescriptorProvider {
    List<Description> getAllDescriptions(Description description, String className) {
        final AllExceptIgnoredTestRunnerBuilder allExceptIgnoredTestRunnerBuilder = new AllExceptIgnoredTestRunnerBuilder();
        try {
            final Class<?> testClass = description.getClass().getClassLoader().loadClass(className);
            Runner runner = allExceptIgnoredTestRunnerBuilder.runnerForClass(testClass);
            if (runner == null) {
                //fall back to default runner
                runner = Request.aClass(testClass).getRunner();
            }
            final Description runnerDescription = runner.getDescription();
            return runnerDescription.getChildren();
        } catch (Throwable throwable) {
            throw new TestSuiteExecutionException(String.format("Unable to process Ignored class %s.", className), throwable);
        }
    }
}
