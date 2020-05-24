/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.configuration.internal;

import org.gradle.api.Action;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultUserCodeApplicationContext implements UserCodeApplicationContext {

    private static final AtomicLong COUNTER = new AtomicLong();

    private final ThreadLocal<CurrentApplication> currentApplication = new ThreadLocal<>();

    @Override
    @Nullable
    public UserCodeApplicationId current() {
        CurrentApplication current = this.currentApplication.get();
        return current == null ? null : current.id;
    }

    @Nullable
    @Override
    public DisplayName currentDisplayName() {
        CurrentApplication current = this.currentApplication.get();
        return current == null ? null : current.displayName;
    }

    @Override
    public void apply(DisplayName displayName, Action<? super UserCodeApplicationId> action) {
        CurrentApplication current = currentApplication.get();
        UserCodeApplicationId id = id();
        currentApplication.set(new CurrentApplication(id, displayName));
        try {
            action.execute(id);
        } finally {
            currentApplication.set(current);
        }
    }

    @Override
    public void reapply(UserCodeApplicationId id, Runnable runnable) {
        CurrentApplication current = currentApplication.get();
        currentApplication.set(new CurrentApplication(id, null));
        try {
            runnable.run();
        } finally {
            currentApplication.set(current);
        }
    }

    @Override
    public <T> Action<T> decorateWithCurrent(final Action<T> action) {
        final UserCodeApplicationId id = current();
        if (id == null) {
            return action;
        }

        return new Action<T>() {
            @Override
            public void execute(T t) {
                CurrentApplication current = currentApplication.get();
                currentApplication.set(new CurrentApplication(id, null));
                try {
                    action.execute(t);
                } finally {
                    currentApplication.set(current);
                }
            }
        };
    }

    private static UserCodeApplicationId id() {
        return new UserCodeApplicationId(COUNTER.incrementAndGet());
    }

    private static class CurrentApplication {
        final UserCodeApplicationId id;
        @Nullable
        final DisplayName displayName;

        public CurrentApplication(UserCodeApplicationId id, @Nullable DisplayName displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }
}
