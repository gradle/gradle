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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.Serializable;
import java.net.URLClassLoader;

public class JUnitTestFramework implements TestFramework {
    private JUnitOptions options;
    private JUnitDetector detector;
    private final Test testTask;
    private DefaultTestFilter filter;

    public JUnitTestFramework(Test testTask, DefaultTestFilter filter) {
        this.testTask = testTask;
        this.filter = filter;
        options = new JUnitOptions();
        detector = new JUnitDetector(new ClassFileExtractionManager(testTask.getTemporaryDirFactory()));
    }

    public WorkerTestClassProcessorFactory getProcessorFactory() {
        verifyJUnitCategorySupport();
        verifyJUnitFilteringSupport();
        return new TestClassProcessorFactoryImpl(new JUnitSpec(options, filter.getIncludePatterns()));
    }

    private void verifyJUnitCategorySupport() {
        if (!options.getExcludeCategories().isEmpty() || !options.getIncludeCategories().isEmpty()) {
            try {
                getTestClassLoader().loadClass("org.junit.experimental.categories.Category");
            } catch (ClassNotFoundException e) {
                throw new GradleException("JUnit Categories defined but declared JUnit version does not support Categories.");
            }
        }
    }

    private void verifyJUnitFilteringSupport() {
        if (!filter.getIncludePatterns().isEmpty()) {
            try {
                Class<?> descriptionClass = getTestClassLoader().loadClass("org.junit.runner.Description");
                descriptionClass.getMethod("getClassName");
            } catch (ClassNotFoundException e) { //JUnit 3.8.1
                filteringNotSupported();
            } catch (NoSuchMethodException e) { //JUnit 4.5-
                filteringNotSupported();
            } catch (Exception e) {
                throw new RuntimeException("Problem encountered when detecting support for JUnit filtering.", e);
            }
        }
    }

    private URLClassLoader getTestClassLoader() {
        return new URLClassLoader(new DefaultClassPath(testTask.getClasspath()).getAsURLArray(), null);
    }

    private void filteringNotSupported() {
        throw new GradleException("Test filtering is not supported for given version of JUnit. Please upgrade JUnit version to at least 4.6.");
    }

    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("junit.framework");
                workerProcessBuilder.sharedPackages("junit.extensions");
                workerProcessBuilder.sharedPackages("org.junit");
            }
        };
    }

    public JUnitOptions getOptions() {
        return options;
    }

    void setOptions(JUnitOptions options) {
        this.options = options;
    }

    public JUnitDetector getDetector() {
        return detector;
    }

    private static class TestClassProcessorFactoryImpl implements WorkerTestClassProcessorFactory, Serializable {
        private final JUnitSpec spec;

        public TestClassProcessorFactoryImpl(JUnitSpec spec) {
            this.spec = spec;
        }

        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            return new JUnitTestClassProcessor(spec, serviceRegistry.get(IdGenerator.class), serviceRegistry.get(ActorFactory.class), new JULRedirector());
        }
    }
}
