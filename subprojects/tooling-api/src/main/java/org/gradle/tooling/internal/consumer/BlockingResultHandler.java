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

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.util.UncheckedException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class BlockingResultHandler<T> implements ResultHandler<T> {
    private final BlockingQueue<Object> queue = new ArrayBlockingQueue<Object>(1);
    private final Class<T> resultType;
    private static final Object NULL = new Object();

    public BlockingResultHandler(Class<T> resultType) {
        this.resultType = resultType;
    }

    public T getResult() {
        Object result;
        try {
            result = queue.take();
        } catch (InterruptedException e) {
            throw UncheckedException.asUncheckedException(e);
        }

        if (result instanceof Throwable) {
            throw UncheckedException.asUncheckedException((Throwable) result);
        }
        if (result == NULL) {
            return null;
        }
        return resultType.cast(result);
    }

    public void onComplete(T result) {
        queue.add(result == null ? NULL : result);
    }

    public void onFailure(GradleConnectionException failure) {
        queue.add(failure);
    }
}
