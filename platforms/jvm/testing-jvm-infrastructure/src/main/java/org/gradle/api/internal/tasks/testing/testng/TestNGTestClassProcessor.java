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

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.RequiresTestFrameworkTestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TestNGTestClassProcessor implements RequiresTestFrameworkTestClassProcessor {
    private final List<Class<?>> testClasses = new ArrayList<>();
    private final File testReportDir;
    private final TestNGSpec spec;
    private final List<File> suiteFiles;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;
    private final ActorFactory actorFactory;
    private ClassLoader applicationClassLoader;
    private Actor resultProcessorActor;
    private TestResultProcessor resultProcessor;
    private boolean startedProcessing;

    public TestNGTestClassProcessor(File testReportDir, TestNGSpec spec, List<File> suiteFiles, IdGenerator<?> idGenerator, Clock clock, ActorFactory actorFactory) {
        this.testReportDir = testReportDir;
        this.spec = spec;
        this.suiteFiles = suiteFiles;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.actorFactory = actorFactory;
    }

    @Override
    public void assertTestFrameworkAvailable() {
        try {
            Class.forName("org.testng.TestNG");
        } catch (ClassNotFoundException e) {
            throw new TestFrameworkNotAvailableException("Failed to load TestNG.  Please ensure that TestNG is available on the test runtime classpath.");
        }
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        assertTestFrameworkAvailable();

        // Wrap the processor in an actor, to make it thread-safe
        resultProcessorActor = actorFactory.createBlockingActor(resultProcessor);
        this.resultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
        if (spec.isDryRun()) {
            System.setProperty("testng.mode.dryrun", "true");
        }

        startedProcessing = true;
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        if (startedProcessing) {
            // TODO - do this inside some 'testng' suite, so that failures and logging are attached to 'testng' rather than some 'test worker'
            try {
                testClasses.add(applicationClassLoader.loadClass(testClass.getTestClassName()));
            } catch (Throwable e) {
                throw new GradleException(String.format("Could not load test class '%s'.", testClass.getTestClassName()), e);
            }
        }
    }

    @Override
    public void stop() {
        if (startedProcessing) {
            try {
                new TestNGTestRunner(
                    testReportDir,
                    suiteFiles,
                    idGenerator,
                    clock,
                    resultProcessor,
                    applicationClassLoader,
                    spec,
                    testClasses
                ).runTests();
            } finally {
                resultProcessorActor.stop();
            }
        }
    }

    @Override
    public void stopNow() {
        throw new UnsupportedOperationException("stopNow() should not be invoked on remote worker TestClassProcessor");
    }
}
