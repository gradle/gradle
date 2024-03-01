/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.code;

import org.gradle.api.Action;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class DefaultUserCodeApplicationContext implements UserCodeApplicationContext {

    private static final AtomicLong COUNTER = new AtomicLong();

    private final ThreadLocal<CurrentApplication> currentApplication = new ThreadLocal<CurrentApplication>();

    @Override
    @Nullable
    public Application current() {
        return this.currentApplication.get();
    }

    @Override
    public void apply(UserCodeSource source, Action<? super UserCodeApplicationId> action) {
        CurrentApplication current = currentApplication.get();
        UserCodeApplicationId id = id();
        currentApplication.set(new CurrentApplication(id, source));
        try {
            action.execute(id);
        } finally {
            currentApplication.set(current);
        }
    }

    @Override
    public void gradleRuntime(Runnable runnable) {
        CurrentApplication current = currentApplication.get();
        currentApplication.set(null);
        try {
            runnable.run();
        } finally {
            currentApplication.set(current);
        }
    }

    @Override
    public <T> Action<T> reapplyCurrentLater(final Action<T> action) {
        final CurrentApplication current = currentApplication.get();
        if (current == null) {
            return action;
        }
        return current.reapplyLater(action);
    }

    private static UserCodeApplicationId id() {
        return new UserCodeApplicationId(COUNTER.incrementAndGet());
    }

    private class CurrentApplication implements Application {
        private final UserCodeApplicationId id;
        private final UserCodeSource source;

        public CurrentApplication(UserCodeApplicationId id, UserCodeSource source) {
            this.id = id;
            this.source = source;
        }

        @Override
        public UserCodeApplicationId getId() {
            return id;
        }

        @Override
        public UserCodeSource getSource() {
            return source;
        }

        @Override
        public void reapply(Runnable runnable) {
            CurrentApplication current = currentApplication.get();
            currentApplication.set(this);
            try {
                runnable.run();
            } finally {
                currentApplication.set(current);
            }
        }

        @Override
        public <T> T reapply(Supplier<T> action) {
            CurrentApplication current = currentApplication.get();
            currentApplication.set(this);
            try {
                return action.get();
            } finally {
                currentApplication.set(current);
            }
        }

        @Override
        public <T> Action<T> reapplyLater(final Action<T> action) {
            return new Action<T>() {
                @Override
                public void execute(T t) {
                    CurrentApplication current = currentApplication.get();
                    currentApplication.set(CurrentApplication.this);
                    try {
                        action.execute(t);
                    } finally {
                        currentApplication.set(current);
                    }
                }
            };
        }
    }
}
