/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.internal.InternalTransformer;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.TestExecutionException;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.TestSpecs;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultDebugOptions;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.protocol.test.InternalTaskSpec;
import org.gradle.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultTestLauncher extends AbstractLongRunningOperation<DefaultTestLauncher> implements TestLauncher {

    private final AsyncConsumerActionExecutor connection;
    private final Set<TestOperationDescriptor> operationDescriptors = new LinkedHashSet<TestOperationDescriptor>();
    private final Set<String> testClassNames = new LinkedHashSet<String>();
    private final Set<InternalJvmTestRequest> internalJvmTestRequests = new LinkedHashSet<InternalJvmTestRequest>();
    private final DefaultDebugOptions debugOptions = new DefaultDebugOptions();
    private final Map<String, List<InternalJvmTestRequest>> tasksAndTests = new HashMap<String, List<InternalJvmTestRequest>>();
    private boolean isRunDefaultTasks = false;
    private final List<InternalTaskSpec> taskSpecs = new ArrayList<InternalTaskSpec>();

    public DefaultTestLauncher(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        operationParamsBuilder.setTasks(Collections.<String>emptyList());
        operationParamsBuilder.setEntryPoint("TestLauncher API");
        this.connection = connection;
    }

    @Override
    protected DefaultTestLauncher getThis() {
        return this;
    }

    @Override
    public TestLauncher withTests(TestOperationDescriptor... testDescriptors) {
        withTests(Arrays.asList(testDescriptors));
        return this;
    }

    @Override
    public TestLauncher withTests(Iterable<? extends TestOperationDescriptor> descriptors) {
        operationDescriptors.addAll(CollectionUtils.toList(descriptors));
        return this;
    }

    @Override
    public TestLauncher withJvmTestClasses(String... classNames) {
        withJvmTestClasses(Arrays.asList(classNames));
        return this;
    }

    @Override
    public TestLauncher withJvmTestClasses(Iterable<String> testClasses) {
        List<InternalJvmTestRequest> newRequests = CollectionUtils.collect(testClasses, new InternalTransformer<InternalJvmTestRequest, String>() {
            @Override
            public InternalJvmTestRequest transform(String testClass) {
                return new DefaultInternalJvmTestRequest(testClass, null, null);
            }
        });
        internalJvmTestRequests.addAll(newRequests);
        testClassNames.addAll(CollectionUtils.toList(testClasses));
        return this;
    }

    @Override
    public TestLauncher withJvmTestMethods(String testClass, String... methods) {
        withJvmTestMethods(testClass, Arrays.asList(methods));
        return this;
    }

    @Override
    public TestLauncher withJvmTestMethods(final String testClass, Iterable<String> methods) {
        List<InternalJvmTestRequest> newRequests = CollectionUtils.collect(methods, new InternalTransformer<InternalJvmTestRequest, String>() {
            @Override
            public InternalJvmTestRequest transform(String methodName) {
                return new DefaultInternalJvmTestRequest(testClass, methodName, null);
            }
        });
        this.internalJvmTestRequests.addAll(newRequests);
        this.testClassNames.add(testClass);
        return this;
    }

    @Override
    public TestLauncher withTaskAndTestClasses(String task, Iterable<String> testClasses) {
        List<InternalJvmTestRequest> tests = CollectionUtils.collect(testClasses, new InternalTransformer<InternalJvmTestRequest, String>() {
            @Override
            public InternalJvmTestRequest transform(String testClass) {
                return new DefaultInternalJvmTestRequest(testClass, null, null);
            }
        });

        addTests(task, tests);
        return this;
    }

    @Override
    public TestLauncher withTaskAndTestMethods(String task, final String testClass, Iterable<String> methods) {
        List<InternalJvmTestRequest> tests = CollectionUtils.collect(methods, new InternalTransformer<InternalJvmTestRequest, String>() {
            @Override
            public InternalJvmTestRequest transform(String methodName) {
                return new DefaultInternalJvmTestRequest(testClass, methodName, null);
            }
        });
        addTests(task, tests);
        return this;
    }

    private void addTests(String task, List<InternalJvmTestRequest> tests) {
        List<InternalJvmTestRequest> existing = tasksAndTests.get(task);
        if (existing == null) {
            tasksAndTests.put(task, tests);
        } else {
            existing.addAll(tests);
            tasksAndTests.put(task, existing);
        }
    }

    @Override
    public TestLauncher debugTestsOn(int port) {
        this.debugOptions.setPort(port);
        return this;
    }

    @Override
    public TestLauncher forTasks(String... tasks) {
        this.isRunDefaultTasks = tasks.length == 0;
        for (String task : tasks) {
            taskSpecs.add(new DefaultTaskSpec(task));
        }
        return this;
    }

    @Override
    public void run() {
        BlockingResultHandler<Void> handler = new BlockingResultHandler<Void>(Void.class);
        run(handler);
        handler.getResult();
    }

    @Override
    public void run(final ResultHandler<? super Void> handler) {
        if (operationDescriptors.isEmpty() && internalJvmTestRequests.isEmpty() && tasksAndTests.isEmpty() && taskSpecs.isEmpty()) {
            throw new TestExecutionException("No test declared for execution.");
        }
        for (Map.Entry<String, List<InternalJvmTestRequest>> entry : tasksAndTests.entrySet()) {
            if (entry.getValue().isEmpty()) {
                throw new TestExecutionException("No test for task " + entry.getKey() + " declared for execution.");
            }
        }
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();
        final TestExecutionRequest testExecutionRequest = new TestExecutionRequest(
            operationDescriptors,
            ImmutableList.copyOf(testClassNames),
            ImmutableSet.copyOf(internalJvmTestRequests),
            debugOptions, ImmutableMap.copyOf(tasksAndTests),
            isRunDefaultTasks,
            taskSpecs
        );
        connection.run(new ConsumerAction<Void>() {
            @Override
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            @Override
            public Void run(ConsumerConnection connection) {
                connection.runTests(testExecutionRequest, getParameters());
                return null;
            }
        }, new ResultHandlerAdapter(handler));
    }

    private class ResultHandlerAdapter extends org.gradle.tooling.internal.consumer.ResultHandlerAdapter<Void> {
        public ResultHandlerAdapter(ResultHandler<? super Void> handler) {
            super(handler, new ConnectionExceptionTransformer(new ConnectionExceptionTransformer.ConnectionFailureMessageProvider() {
                @Override
                public String getConnectionFailureMessage(Throwable throwable) {
                    return String.format("Could not execute tests using %s.", connection.getDisplayName());
                }
            }));
        }
    }

    @Override
    public TestLauncher withTestsFor(Action<TestSpecs> testSpec) {
        DefaultTestSpecs testSpecs = new DefaultTestSpecs();
        testSpec.execute(testSpecs);
        taskSpecs.addAll(testSpecs.getTestSpecs());
        return this;
    }
}
