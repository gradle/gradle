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
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.UncheckedException;
import org.gradle.util.DisconnectableInputStream;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultCancellableOperationManager implements CancellableOperationManager {

    private static final int EOF = -1;
    private static final int KEY_CODE_CTRL_D = 4;

    private final ExecutorService executorService;
    private final DisconnectableInputStream input;
    private final BuildCancellationToken cancellationToken;

    public DefaultCancellableOperationManager(ExecutorService executorService, DisconnectableInputStream input, BuildCancellationToken cancellationToken) {
        this.executorService = executorService;
        this.input = input;
        this.cancellationToken = cancellationToken;
    }

    @Override
    public void monitorInput(final Action<? super BuildCancellationToken> operation) {
        final AtomicBoolean operationCompleted = new AtomicBoolean();
        Future<?> handle = null;
        try {
            handle = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            int c = input.read();
                            // Ignore input received after the monitor operation has been completed
                            if (operationCompleted.get()) {
                                break;
                            }
                            if (isCancellation(c) && !operationCompleted.get()) {
                                cancellationToken.cancel();
                                break;
                            }
                        }
                    } catch (IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            });
            operation.execute(cancellationToken);
            operationCompleted.set(true);
        } finally {
            if (handle != null) {
                handle.cancel(true);
            }
        }
    }

    private static boolean isCancellation(int c) {
        return c == KEY_CODE_CTRL_D || c == EOF;
    }
}
