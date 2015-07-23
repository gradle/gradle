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

import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.TestExecutionException;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.protocol.test.TestExecutionRequest;
import org.gradle.util.CollectionUtils;

import java.util.*;

public class DefaultTestLauncher extends AbstractLongRunningOperation<DefaultTestLauncher> implements TestLauncher {

    private final AsyncConsumerActionExecutor connection;
    private List<OperationDescriptor> operationDescriptors = new ArrayList<OperationDescriptor>();
    private Set<String> testClassNames = new HashSet<String>();

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
    public TestLauncher withTests(OperationDescriptor... testDescriptors) {
        operationDescriptors.addAll(Arrays.asList(testDescriptors));
        return this;
    }

    @Override
    public TestLauncher withJvmTestClasses(String... classNames) {
        testClassNames.addAll(CollectionUtils.toList(classNames));
        return this;
    }

    public void run() {
        BlockingResultHandler<Void> handler = new BlockingResultHandler<Void>(Void.class);
        run(handler);
        handler.getResult();
    }

    public void run(final ResultHandler<? super Void> handler) {
        if(operationDescriptors.isEmpty() && testClassNames.isEmpty()){
            throw new TestExecutionException("No test declared for execution.");
        }
        final ConsumerOperationParameters operationParameters = operationParamsBuilder.setParameters(connectionParameters).build();
        connection.run(new ConsumerAction<Void>() {
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }
            public Void run(ConsumerConnection connection) {
                connection.runTests(new TestExecutionRequest(operationDescriptors, testClassNames), getParameters());
                return null;
            }
        }, new ResultHandlerAdapter(handler));

    }

    private class ResultHandlerAdapter extends org.gradle.tooling.internal.consumer.ResultHandlerAdapter<Void> {
        public ResultHandlerAdapter(ResultHandler<? super Void> handler) {
            super(handler);
        }

        @Override
        protected String connectionFailureMessage(Throwable failure) {
            return String.format("Could not execute tests using %s.", connection.getDisplayName());
        }
    }
}
