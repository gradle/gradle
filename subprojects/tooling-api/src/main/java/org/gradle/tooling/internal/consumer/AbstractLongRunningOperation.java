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

import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class AbstractLongRunningOperation<T extends LongRunningOperation> implements LongRunningOperation {
    protected final ConsumerOperationParameters operationParameters;

    protected AbstractLongRunningOperation(ConsumerOperationParameters operationParameters) {
        this.operationParameters = operationParameters;
    }

    protected abstract T getThis();

    public T withArguments(String... arguments) {
        operationParameters.setArguments(arguments);
        return getThis();
    }

    public T setStandardOutput(OutputStream outputStream) {
        operationParameters.setStandardOutput(outputStream);
        return getThis();
    }

    public T setStandardError(OutputStream outputStream) {
        operationParameters.setStandardError(outputStream);
        return getThis();
    }

    public T setStandardInput(InputStream inputStream) {
        operationParameters.setStandardInput(inputStream);
        return getThis();
    }

    public T setJavaHome(File javaHome) {
        operationParameters.setJavaHome(javaHome);
        return getThis();
    }

    public T setJvmArguments(String... jvmArguments) {
        operationParameters.setJvmArguments(jvmArguments);
        return getThis();
    }

    public T addProgressListener(ProgressListener listener) {
        operationParameters.addProgressListener(listener);
        return getThis();
    }
}
