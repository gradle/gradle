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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Implementation of {@link WorkerTestClassProcessorFactory} which instantiates a {@link TestNGTestClassProcessor}.
 * This class is loaded on test workers themselves and acts as the entry-point to running TestNG tests on a test worker.
 */
class TestNgTestClassProcessorFactory implements WorkerTestClassProcessorFactory, Serializable {
    private final File testReportDir;
    private final TestNGSpec options;
    private final List<File> suiteFiles;

    public TestNgTestClassProcessorFactory(File testReportDir, TestNGSpec options, List<File> suiteFiles) {
        this.testReportDir = testReportDir;
        this.options = options;
        this.suiteFiles = suiteFiles;
    }

    @Override
    public TestClassProcessor create(IdGenerator<?> idGenerator, ActorFactory actorFactory, Clock clock) {
        return new TestNGTestClassProcessor(testReportDir, options, suiteFiles, idGenerator, clock, actorFactory);
    }
}
