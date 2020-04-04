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

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

public class JUnitTestFramework implements TestFramework {
    private JUnitOptions options;
    private final JUnitDetector detector;
    private final DefaultTestFilter filter;

    public JUnitTestFramework(Test testTask, DefaultTestFilter filter) {
        this.filter = filter;
        options = new JUnitOptions();
        detector = new JUnitDetector(new ClassFileExtractionManager(testTask.getTemporaryDirFactory()));
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        return new TestClassProcessorFactoryImpl(new JUnitSpec(
            options.getIncludeCategories(), options.getExcludeCategories(),
            filter.getIncludePatterns(), filter.getExcludePatterns(),
            filter.getCommandLineIncludePatterns()));
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            @Override
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("junit.framework");
                workerProcessBuilder.sharedPackages("junit.extensions");
                workerProcessBuilder.sharedPackages("org.junit");
            }
        };
    }

    @Override
    public List<String> getTestWorkerImplementationModules() {
        return Collections.emptyList();
    }

    @Override
    public JUnitOptions getOptions() {
        return options;
    }

    void setOptions(JUnitOptions options) {
        this.options = options;
    }

    @Override
    public JUnitDetector getDetector() {
        return detector;
    }

    private static class TestClassProcessorFactoryImpl implements WorkerTestClassProcessorFactory, Serializable {
        private final JUnitSpec spec;

        public TestClassProcessorFactoryImpl(JUnitSpec spec) {
            this.spec = spec;
        }

        @Override
        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            return new JUnitTestClassProcessor(spec, serviceRegistry.get(IdGenerator.class), serviceRegistry.get(ActorFactory.class), serviceRegistry.get(Clock.class));
        }
    }
}
