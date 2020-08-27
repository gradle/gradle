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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.testing.TestClassLoaderFactory;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class TestNGTestFramework implements TestFramework {
    private final TestNGOptions options;
    private final TestNGDetector detector;
    private final DefaultTestFilter filter;
    private final ObjectFactory objects;
    private final String testTaskPath;
    private final FileCollection testTaskClasspath;
    private final Factory<File> testTaskTemporaryDir;
    private transient ClassLoader testClassLoader;

    public TestNGTestFramework(final Test testTask, FileCollection classpath, DefaultTestFilter filter, ObjectFactory objects) {
        this.filter = filter;
        this.objects = objects;
        this.testTaskPath = testTask.getPath();
        this.testTaskClasspath = classpath;
        this.testTaskTemporaryDir = testTask.getTemporaryDirFactory();
        options = objects.newInstance(TestNGOptions.class);
        conventionMapOutputDirectory(options, testTask.getReports().getHtml());
        detector = new TestNGDetector(new ClassFileExtractionManager(testTask.getTemporaryDirFactory()));
    }

    private static void conventionMapOutputDirectory(TestNGOptions options, final DirectoryReport html) {
        new DslObject(options).getConventionMapping().map("outputDirectory", new Callable<File>() {
            @Override
            public File call() {
                return html.getDestination();
            }
        });
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        verifyConfigFailurePolicy();
        verifyPreserveOrder();
        verifyGroupByInstances();
        List<File> suiteFiles = options.getSuites(testTaskTemporaryDir.create());
        TestNGSpec spec = new TestNGSpec(options, filter);
        return new TestClassProcessorFactoryImpl(this.options.getOutputDirectory(), spec, suiteFiles);
    }

    private void verifyConfigFailurePolicy() {
        if (!options.getConfigFailurePolicy().equals(TestNGOptions.DEFAULT_CONFIG_FAILURE_POLICY)) {
            verifyMethodExists("setConfigFailurePolicy", String.class,
                String.format("The version of TestNG used does not support setting config failure policy to '%s'.", options.getConfigFailurePolicy()));
        }
    }

    private void verifyPreserveOrder() {
        if (options.getPreserveOrder()) {
            verifyMethodExists("setPreserveOrder", boolean.class, "Preserving the order of tests is not supported by this version of TestNG.");
        }
    }

    private void verifyGroupByInstances() {
        if (options.getGroupByInstances()) {
            verifyMethodExists("setGroupByInstances", boolean.class, "Grouping tests by instances is not supported by this version of TestNG.");
        }
    }

    private void verifyMethodExists(String methodName, Class<?> parameterType, String failureMessage) {
        try {
            createTestNg().getMethod(methodName, parameterType);
        } catch (NoSuchMethodException e) {
            throw new InvalidUserDataException(failureMessage, e);
        }
    }

    private Class<?> createTestNg() {
        if (testClassLoader == null) {
            TestClassLoaderFactory factory = objects.newInstance(
                TestClassLoaderFactory.class,
                testTaskPath,
                testTaskClasspath
            );
            testClassLoader = factory.create();
        }
        try {
            return testClassLoader.loadClass("org.testng.TestNG");
        } catch (ClassNotFoundException e) {
            throw new GradleException("Could not load TestNG.", e);
        }
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            @Override
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("org.testng");
            }
        };
    }

    @Override
    public List<String> getTestWorkerImplementationModules() {
        return Collections.emptyList();
    }

    @Override
    public TestNGOptions getOptions() {
        return options;
    }

    @Override
    public TestNGDetector getDetector() {
        return detector;
    }

    private static class TestClassProcessorFactoryImpl implements WorkerTestClassProcessorFactory, Serializable {
        private final File testReportDir;
        private final TestNGSpec options;
        private final List<File> suiteFiles;

        public TestClassProcessorFactoryImpl(File testReportDir, TestNGSpec options, List<File> suiteFiles) {
            this.testReportDir = testReportDir;
            this.options = options;
            this.suiteFiles = suiteFiles;
        }

        @Override
        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            return new TestNGTestClassProcessor(testReportDir, options, suiteFiles, serviceRegistry.get(IdGenerator.class), serviceRegistry.get(Clock.class), serviceRegistry.get(ActorFactory.class));
        }
    }
}
