/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.docs.samples;

import org.gradle.api.GradleException;
import org.gradle.exemplar.executor.CommandExecutor;
import org.gradle.exemplar.executor.ExecutionMetadata;
import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SamplesRunner;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.model.InitializationError;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

@NullMarked
class IntegrationTestSamplesRunner extends SamplesRunner {
    private static final String SAMPLES_DIR_PROPERTY = "integTest.samplesdir";

    public IntegrationTestSamplesRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected CommandExecutor selectExecutor(ExecutionMetadata executionMetadata, File workingDir, Command command) {
        return new IntegrationTestSamplesExecutor(workingDir, command.isExpectFailure());
    }

    @Nullable
    @Override
    protected File getImplicitSamplesRootDir() {
        String samplesDir = System.getProperty(SAMPLES_DIR_PROPERTY);
        if (samplesDir == null) {
            throw new IllegalStateException(String.format("'%s' property is required", SAMPLES_DIR_PROPERTY));
        }
        return Paths.get(samplesDir).toFile();
    }

    public List<Sample> getAllSamples() {
        return getChildren();
    }

    private class RunNotifierAdapter extends RunNotifier {
        @Override
        public void fireTestFailure(Failure failure) {
            Throwable t = failure.getException();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RuntimeException(t);
            }
        }

        @Override
        public void fireTestIgnored(final Description description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fireTestStarted(final Description description) throws StoppedByUserException {
        }

        @Override
        public void fireTestFinished(final Description description) {
        }
    }

    public void runSample(Sample sample) {
        try {
            runChild(sample, new RunNotifierAdapter());
        } catch (Throwable e) {
            String extraParameter = "configCache".equals(System.getProperty("org.gradle.integtest.executer")) ?
                "-PenableConfigurationCacheForDocsTests=true" : "";
            throw new GradleException(
                "Sample test run failed.\nTo understand how docsTest works, See:\n" +
                    "  https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/README.md#testing-docs\n" +
                    "To reproduce this failure, run:\n" +
                    "  ./gradlew docs:docsTest --tests '*" + sample.getId() + "*' " + extraParameter, e);
        }
    }
}
