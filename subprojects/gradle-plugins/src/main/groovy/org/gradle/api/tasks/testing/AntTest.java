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

package org.gradle.api.tasks.testing;

import org.gradle.api.GradleException;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.testing.TestSummaryListener;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.TestClassProcessorFactory;
import org.gradle.api.testing.detection.DefaultTestClassScannerFactory;
import org.gradle.api.testing.detection.TestClassScanner;
import org.gradle.api.testing.detection.TestClassScannerFactory;
import org.gradle.api.testing.execution.RestartEveryNTestClassProcessor;
import org.gradle.api.testing.execution.fork.ForkingTestClassProcessor;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.messaging.TcpMessagingServer;
import org.gradle.process.DefaultWorkerProcessFactory;
import org.gradle.process.WorkerProcessFactory;
import org.slf4j.LoggerFactory;

/**
 * A task for executing JUnit (3.8.x or 4.x) or TestNG tests.
 *
 * @author Hans Dockter
 */
public class AntTest extends AbstractTestTask {
    private TestClassScannerFactory testClassScannerFactory;

    public AntTest() {
        this.testClassScannerFactory = new DefaultTestClassScannerFactory();
    }

    void setTestClassScannerFactory(TestClassScannerFactory testClassScannerFactory) {
        this.testClassScannerFactory = testClassScannerFactory;
    }

    public void executeTests() {
        ClassPathRegistry classPathRegistry = getServices().get(ClassPathRegistry.class);

        TcpMessagingServer server = new TcpMessagingServer(TestClassProcessor.class.getClassLoader());
        final WorkerProcessFactory workerFactory = new DefaultWorkerProcessFactory(server, classPathRegistry,
                getServices().get(FileResolver.class));

        final TestFrameworkInstance testFrameworkInstance = getTestFramework();
        final TestClassProcessorFactory testInstanceFactory = testFrameworkInstance.getProcessorFactory();
        TestClassProcessorFactory processorFactory = new TestClassProcessorFactory() {
            public TestClassProcessor create() {
                return new ForkingTestClassProcessor(workerFactory, testInstanceFactory, getOptions().createForkOptions(), getClasspath(), testFrameworkInstance.getWorkerConfigurationAction());
            }
        };

        TestClassProcessor processor;
        if (getForkEvery() != null) {
            processor = new RestartEveryNTestClassProcessor(processorFactory, getForkEvery());
        } else {
            processor = processorFactory.create();
        }

        TestSummaryListener listener = new TestSummaryListener(LoggerFactory.getLogger(AntTest.class));
        addTestListener(listener);

        processor.startProcessing(getTestListenerBroadcaster().getSource());
        TestClassScanner testClassScanner = testClassScannerFactory.createTestClassScanner(this, processor);
        testClassScanner.run();

        server.stop();

        testFrameworkInstance.report();

        if (!isIgnoreFailures() && listener.hadFailures()) {
            throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
        }
    }
}
