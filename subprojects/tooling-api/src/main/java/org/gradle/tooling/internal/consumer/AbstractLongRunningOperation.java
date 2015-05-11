/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.base.Preconditions;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressEventType;
import org.gradle.tooling.events.internal.BuildOperationProgressListener;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.internal.TaskProgressListener;
import org.gradle.tooling.events.test.TestProgressEvent;
import org.gradle.tooling.events.test.internal.TestProgressListener;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;

public abstract class AbstractLongRunningOperation<T extends AbstractLongRunningOperation<T>> implements LongRunningOperation {
    protected final ConnectionParameters connectionParameters;
    protected final ConsumerOperationParameters.Builder operationParamsBuilder;

    protected AbstractLongRunningOperation(ConnectionParameters parameters) {
        connectionParameters = parameters;
        operationParamsBuilder = ConsumerOperationParameters.builder();
        operationParamsBuilder.setCancellationToken(new DefaultCancellationTokenSource().token());
    }

    protected abstract T getThis();

    protected final ConsumerOperationParameters getConsumerOperationParameters() {
        ConnectionParameters connectionParameters = this.connectionParameters;
        return operationParamsBuilder.setParameters(connectionParameters).build();
    }

    public T withArguments(String... arguments) {
        operationParamsBuilder.setArguments(arguments);
        return getThis();
    }

    public T setStandardOutput(OutputStream outputStream) {
        operationParamsBuilder.setStdout(outputStream);
        return getThis();
    }

    public T setStandardError(OutputStream outputStream) {
        operationParamsBuilder.setStderr(outputStream);
        return getThis();
    }

    public T setStandardInput(InputStream inputStream) {
        operationParamsBuilder.setStdin(inputStream);
        return getThis();
    }

    public T setColorOutput(boolean colorOutput) {
        operationParamsBuilder.setColorOutput(colorOutput);
        return getThis();
    }

    public T setJavaHome(File javaHome) {
        operationParamsBuilder.setJavaHome(javaHome);
        return getThis();
    }

    public T setJvmArguments(String... jvmArguments) {
        operationParamsBuilder.setJvmArguments(jvmArguments);
        return getThis();
    }

    public T addProgressListener(ProgressListener listener) {
        operationParamsBuilder.addProgressListener(listener);
        return getThis();
    }

    public T addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        return addProgressListener(listener, EnumSet.allOf(ProgressEventType.class));
    }

    @Override
    public T addProgressListener(org.gradle.tooling.events.ProgressListener listener, EnumSet<ProgressEventType> eventTypes) {
        AllOperationsProgressListener delegatingListener = new AllOperationsProgressListener(listener);
        if (eventTypes.contains(ProgressEventType.TEST)) {
            addTestProgressListener(delegatingListener);
        }
        if (eventTypes.contains(ProgressEventType.TASK)) {
            addTaskProgressListener(delegatingListener);
        }
        if (eventTypes.contains(ProgressEventType.GENERIC)) {
            addBuildOperationProgressListeners(delegatingListener);
        }
        return getThis();
    }

    private T addTestProgressListener(TestProgressListener listener) {
        operationParamsBuilder.addTestProgressListener(listener);
        return getThis();
    }

    private T addTaskProgressListener(TaskProgressListener listener) {
        operationParamsBuilder.addTaskProgressListener(listener);
        return getThis();
    }

    private T addBuildOperationProgressListeners(BuildOperationProgressListener listener) {
        operationParamsBuilder.addBuildOperationProgressListeners(listener);
        return getThis();
    }

    public T withCancellationToken(CancellationToken cancellationToken) {
        operationParamsBuilder.setCancellationToken(Preconditions.checkNotNull(cancellationToken));
        return getThis();
    }

    private static final class AllOperationsProgressListener implements TestProgressListener, TaskProgressListener, BuildOperationProgressListener {
        private final org.gradle.tooling.events.ProgressListener listener;

        private AllOperationsProgressListener(org.gradle.tooling.events.ProgressListener listener) {
            this.listener = listener;
        }

        @Override
        public void statusChanged(TestProgressEvent event) {
            listener.statusChanged(event);
        }

        @Override
        public void statusChanged(TaskProgressEvent event) {
            listener.statusChanged(event);
        }

        @Override
        public void statusChanged(ProgressEvent event) {
            listener.statusChanged(event);
        }
    }
}
