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

package org.gradle.api.internal.tasks.testing.worker;

import org.gradle.api.Action;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(JMock.class)
public class ForkingTestClassProcessorTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final WorkerTestClassProcessorFactory processorFactory = context.mock(WorkerTestClassProcessorFactory.class);
    private final Factory<WorkerProcessBuilder> workerFactory = context.mock(Factory.class);
    private final WorkerProcess workerProcess = context.mock(WorkerProcess.class);
    private final RemoteTestClassProcessor worker = context.mock(RemoteTestClassProcessor.class);
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
            one(worker).stop();
            one(workerProcess).waitForStop();
        }});

        processor.startProcessing(resultProcessor);
        processor.processTestClass(test1);
        processor.stop();
    }

    @Test
    public void onEndProcessingDoesNothingIfNoTestsProcessed() {
        processor.startProcessing(resultProcessor);
        processor.stop();
    }

    private void expectWorkerProcessStarted() {
        context.checking(new Expectations() {{
            WorkerProcessBuilder builder = context.mock(WorkerProcessBuilder.class);
            ObjectConnection connection = context.mock(ObjectConnection.class);
            JavaExecHandleBuilder javaCommandBuilder = context.mock(JavaExecHandleBuilder.class);

            one(workerFactory).create();
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
            
            one(connection).addOutgoing(RemoteTestClassProcessor.class);
            will(returnValue(worker));

            one(workerProcess).start();

            one(worker).startProcessing();
        }});
    }
}
