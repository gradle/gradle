/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.work;

import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

public class WaitBuildOperationFiringSynchronizer implements Synchronizer {

    private final DisplayName targetDescription;
    private final Synchronizer delegate;
    private final BuildOperationRunner buildOperationRunner;

    public WaitBuildOperationFiringSynchronizer(DisplayName targetDescription, Synchronizer delegate, BuildOperationRunner buildOperationRunner) {
        this.targetDescription = targetDescription;
        this.delegate = delegate;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public void withLock(final Runnable action) {
        final AtomicBoolean successfulWait = new AtomicBoolean(false);
        final BuildOperationContext buildOperationContext = startWaitingOperation();

        try {
            delegate.withLock(new Runnable() {
                @Override
                public void run() {
                    successfulWait.set(true);
                    buildOperationContext.setResult(null);
                    action.run();
                }
            });
        } finally {
            if (!successfulWait.get()) {
                buildOperationContext.setResult(null);
            }
        }
    }

    @Override
    public <T> T withLock(final Factory<T> action) {
        final AtomicBoolean successfulWait = new AtomicBoolean(false);
        final BuildOperationContext buildOperationContext = startWaitingOperation();

        try {
            return delegate.withLock(new Factory<T>() {
                @Nullable
                @Override
                public T create() {
                    successfulWait.set(true);
                    buildOperationContext.setResult(null);
                    return action.create();
                }
            });
        } finally {
            if (!successfulWait.get()) {
                buildOperationContext.setResult(null);
            }
        }

    }

    private BuildOperationContext startWaitingOperation() {
        return buildOperationRunner.start(
            BuildOperationDescriptor
                .displayName("Synchronizer wait: " + targetDescription.getDisplayName())
        );
    }
}
