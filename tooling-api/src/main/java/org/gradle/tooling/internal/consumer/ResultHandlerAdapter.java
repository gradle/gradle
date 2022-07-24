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

import org.gradle.api.Transformer;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1;

/**
 * Adapts a {@link ResultHandler} to a {@link ResultHandlerVersion1}.
 *
 * @param <T> The result type.
 */
public class ResultHandlerAdapter<T> implements ResultHandlerVersion1<T> {
    private final ResultHandler<? super T> handler;
    private final Transformer<GradleConnectionException, Throwable> exceptionTransformer;

    protected ResultHandlerAdapter(ResultHandler<? super T> handler, Transformer<GradleConnectionException, Throwable> exceptionTransformer) {
        this.handler = handler;
        this.exceptionTransformer = exceptionTransformer;
    }

    @Override
    public void onComplete(T result) {
        handler.onComplete(result);
    }

    @Override
    public void onFailure(Throwable failure) {
        handler.onFailure(exceptionTransformer.transform(failure));
    }
}
