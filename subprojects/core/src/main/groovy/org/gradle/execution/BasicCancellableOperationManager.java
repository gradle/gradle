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

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.initialization.BuildCancellationToken;

public class BasicCancellableOperationManager implements CancellableOperationManager {
    private final BuildCancellationToken cancellationToken;

    public BasicCancellableOperationManager(BuildCancellationToken cancellationToken) {
        this.cancellationToken = cancellationToken;
    }

    @Override
    public <T> T monitorInputYield(Transformer<? extends T, ? super BuildCancellationToken> operation) {
        return interruptYield(operation);
    }

    @Override
    public void monitorInputExecute(Action<? super BuildCancellationToken> operation) {
        interruptExecute(operation);
    }

    @Override
    public <T> T interruptYield(final Transformer<? extends T, ? super BuildCancellationToken> operation) {
        final Thread callingThread = Thread.currentThread();
        Runnable cancellationHandler = new Runnable() {
            @Override
            public void run() {
                callingThread.interrupt();
            }
        };
        if (cancellationToken.isCancellationRequested()) {
            cancellationHandler.run();
        }
        cancellationToken.addCallback(cancellationHandler);
        try {
            return operation.transform(cancellationToken);
        } catch (RuntimeException e) {
            boolean causedByInterruptedException = Iterables.any(Throwables.getCausalChain(e), new Predicate<Throwable>() {
                @Override
                public boolean apply(Throwable input) {
                    return input instanceof InterruptedException;
                }
            });
            if (causedByInterruptedException) {
                return null;
            } else {
                throw e;
            }
        } finally {
            cancellationToken.removeCallback(cancellationHandler);
            Thread.interrupted();
        }
    }

    @Override
    public void interruptExecute(final Action<? super BuildCancellationToken> operation) {
        interruptYield(new Transformer<Void, BuildCancellationToken>() {
            @Override
            public Void transform(BuildCancellationToken cancellationToken) {
                operation.execute(cancellationToken);
                return null;
            }
        });
    }

    public BuildCancellationToken getCancellationToken() {
        return cancellationToken;
    }
}
