/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.internal.event.ListenerNotificationException;
import org.gradle.tooling.*;
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException;
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException;

public class ExceptionTransformer implements Transformer<GradleConnectionException, Throwable> {
    private final Transformer<String, Throwable> connectionFailureProvider;

    public ExceptionTransformer(Transformer<String, Throwable> connectionFailureProvider) {
        this.connectionFailureProvider = connectionFailureProvider;
    }

    @Override
    public GradleConnectionException transform(Throwable failure) {
        if (failure instanceof InternalUnsupportedBuildArgumentException) {
            return new UnsupportedBuildArgumentException(connectionFailureMessage(failure)
                + "\n" + failure.getMessage(), failure);
        } else if (failure instanceof UnsupportedOperationConfigurationException) {
            return new UnsupportedOperationConfigurationException(connectionFailureMessage(failure)
                + "\n" + failure.getMessage(), failure.getCause());
        } else if (failure instanceof GradleConnectionException) {
            return (GradleConnectionException) failure;
        } else if (failure instanceof InternalBuildCancelledException) {
            return new BuildCancelledException(connectionFailureMessage(failure), failure.getCause());
        } else if (failure instanceof InternalTestExecutionException) {
            return new TestExecutionException(connectionFailureMessage(failure), failure.getCause());
        } else if (failure instanceof BuildExceptionVersion1) {
            return new BuildException(connectionFailureMessage(failure), failure.getCause());
        } else if (failure instanceof ListenerNotificationException) {
            return new ListenerFailedException(connectionFailureMessage(failure), ((ListenerNotificationException) failure).getCauses());
        } else {
            return new GradleConnectionException(connectionFailureMessage(failure), failure);
        }
    }

    private String connectionFailureMessage(Throwable failure) {
        return connectionFailureProvider.transform(failure);
    }
};
