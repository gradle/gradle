/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.model;

import org.gradle.internal.Factory;
import org.gradle.internal.work.Synchronizer;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Synchronizer} backed by a {@link ModelContainer}'s model lock.
 */
public final class ModelContainerSynchronizer implements Synchronizer {
    private final ModelContainer<?> container;

    public ModelContainerSynchronizer(ModelContainer<?> container) {
        this.container = container;
    }

    @Override
    public void withLock(Runnable action) {
        container.runWithModelLock(() -> {
            action.run();
            return null;
        });
    }

    @Override
    public <T extends @Nullable Object> T withLock(Factory<T> action) {
        return container.runWithModelLock(action::create);
    }
}
