/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException;
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;

/**
 * Adapts a {@link ResultHandler} to a {@link ResultHandlerVersion1}.
 *
 * @param <T> The result type.
 */
abstract class ResultHandlerAdapter<T> implements ResultHandlerVersion1<T> {
    private final ResultHandler<? super T> handler;

    ResultHandlerAdapter(ResultHandler<? super T> handler) {
        this.handler = handler;
    }

    public void onComplete(T result) {
        handler.onComplete(result);
    }

    public void onFailure(Throwable failure) {
        if (failure instanceof InternalUnsupportedBuildArgumentException) {
            handler.onFailure(new UnsupportedBuildArgumentException(connectionFailureMessage(failure)
                    + "\n" + failure.getMessage(), failure));
        } else if (failure instanceof UnsupportedOperationConfigurationException) {
            handler.onFailure(new UnsupportedOperationConfigurationException(connectionFailureMessage(failure)
                    + "\n" + failure.getMessage(), failure.getCause()));
        } else if (failure instanceof GradleConnectionException) {
            handler.onFailure((GradleConnectionException) failure);
        } else if (failure instanceof BuildExceptionVersion1) {
            handler.onFailure(new BuildException(connectionFailureMessage(failure), failure.getCause()));
        } else {
            handler.onFailure(new GradleConnectionException(connectionFailureMessage(failure), failure));
        }
    }

    protected abstract String connectionFailureMessage(Throwable failure);
}
