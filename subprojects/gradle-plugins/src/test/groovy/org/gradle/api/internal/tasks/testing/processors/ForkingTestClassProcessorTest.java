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

package org.gradle.api.internal.tasks.testing.processors;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.worker.TestWorker;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.messaging.ObjectConnection;
import org.gradle.process.WorkerProcess;
import org.gradle.process.WorkerProcessBuilder;
import org.gradle.process.WorkerProcessFactory;
import org.gradle.util.exec.JavaExecHandleBuilder;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static java.util.Arrays.*;
import static org.hamcrest.Matchers.*;

@RunWith(JMock.class)
public class ForkingTestClassProcessorTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final WorkerTestClassProcessorFactory processorFactory = context.mock(WorkerTestClassProcessorFactory.class);
    private final WorkerProcessFactory workerFactory = context.mock(WorkerProcessFactory.class);
    private final WorkerProcess workerProcess = context.mock(WorkerProcess.class);
    private final TestClassProcessor worker = context.mock(TestClassProcessor.class);
    private final TestClassRunInfo test1 = context.mock(TestClassRunInfo.class, "test1");
    private final TestClassRunInfo test2 = context.mock(TestClassRunInfo.class, "test2");
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class);
    private final List<File> appClassPath = asList(new File("classpath.jar"));
    private final JavaForkOptions options = context.mock(JavaForkOptions.class);
    private final Action<WorkerProcessBuilder> action = context.mock(Action.class);
    private final ForkingTestClassProcessor processor = new ForkingTestClassProcessor(workerFactory, processorFactory, options, appClassPath, action);

    @Test
    public void onFirstTestCaseStartsWorkerProcess() {
        expectWorkerProcessStarted();
        context.checking(new Expectations() {{
            one(worker).processTestClass(test1);
        }});

        processor.startProcessing(resultProcessor);
        processor.processTestClass(test1);
    }

    @Test
    public void onSubsequentTestCaseForwardsTestToWorkerProcess() {
        expectWorkerProcessStarted();
        context.checking(new Expectations() {{
            one(worker).processTestClass(test1);
            one(worker).processTestClass(test2);
        }});

        processor.startProcessing(resultProcessor);
        processor.processTestClass(test1);
        processor.processTestClass(test2);
    }

    @Test
    public void onEndProcessingWaitsForWorkerProcessToStop() {
        expectWorkerProcessStarted();
        context.checking(new Expectations() {{
            one(worker).processTestClass(test1);
            one(worker).endProcessing();
            one(workerProcess).waitForStop();
        }});

        processor.startProcessing(resultProcessor);
        processor.processTestClass(test1);
        processor.endProcessing();
    }

    @Test
    public void onEndProcessingDoesNothingIfNoTestsProcessed() {
        processor.startProcessing(resultProcessor);
        processor.endProcessing();
    }

    private void expectWorkerProcessStarted() {
        context.checking(new Expectations() {{
            WorkerProcessBuilder builder = context.mock(WorkerProcessBuilder.class);
            ObjectConnection connection = context.mock(ObjectConnection.class);
            JavaExecHandleBuilder javaCommandBuilder = new JavaExecHandleBuilder();

            one(workerFactory).newProcess();
            will(returnValue(builder));

            one(builder).worker(with(notNullValue(TestWorker.class)));

            one(builder).applicationClasspath(appClassPath);

            one(builder).setLoadApplicationInSystemClassLoader(true);

            one(action).execute(builder);
            
            allowing(builder).getJavaCommand();
            will(returnValue(javaCommandBuilder));

            one(options).copyTo(javaCommandBuilder);

            one(builder).build();
            will(returnValue(workerProcess));

            allowing(workerProcess).getConnection();
            will(returnValue(connection));

            one(connection).addIncoming(TestResultProcessor.class, resultProcessor);
            
            one(connection).addOutgoing(TestClassProcessor.class);
            will(returnValue(worker));

            one(workerProcess).start();
        }});
    }
}
