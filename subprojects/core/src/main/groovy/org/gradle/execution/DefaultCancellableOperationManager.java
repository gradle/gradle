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

package org.gradle.execution;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.UncheckedException;
import org.gradle.util.DisconnectableInputStream;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class DefaultCancellableOperationManager extends BasicCancellableOperationManager {

    private static final int EOF = -1;
    private static final int KEY_CODE_CTRL_D = 4;

    private final ExecutorService executorService;
    private final DisconnectableInputStream input;

    public DefaultCancellableOperationManager(ExecutorService executorService, DisconnectableInputStream input, BuildCancellationToken cancellationToken) {
        super(cancellationToken);
        this.executorService = executorService;
        this.input = input;
    }

    @Override
    public <T> T monitorInputYield(Transformer<? extends T, ? super BuildCancellationToken> operation) {
        Future<?> handle = null;
        try {
            handle = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            int c = input.read();
                            if (c == KEY_CODE_CTRL_D || c == EOF) {
                                getCancellationToken().cancel();
                                break;
                            }
                        }
                    } catch (IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            });
            return interruptYield(operation);
        } finally {
            if (handle != null) {
                handle.cancel(true);
            }
        }
    }

    @Override
    public void monitorInputExecute(final Action<? super BuildCancellationToken> operation) {
        monitorInputYield(new Transformer<Void, BuildCancellationToken>() {
            @Override
            public Void transform(BuildCancellationToken cancellationToken) {
                operation.execute(cancellationToken);
                return null;
            }
        });
    }
}
