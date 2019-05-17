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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Action;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultUserCodeApplicationContext implements UserCodeApplicationContext {

    private static final AtomicLong COUNTER = new AtomicLong();

    private final ThreadLocal<Deque<UserCodeApplicationId>> stackThreadLocal = new ThreadLocal<Deque<UserCodeApplicationId>>() {
        @Override
        protected Deque<UserCodeApplicationId> initialValue() {
            return new ArrayDeque<UserCodeApplicationId>();
        }
    };

    @Override
    @Nullable
    public UserCodeApplicationId current() {
        return stackThreadLocal.get().peek();
    }

    @Override
    public void apply(Action<? super UserCodeApplicationId> action) {
        Deque<UserCodeApplicationId> stack = stackThreadLocal.get();
        UserCodeApplicationId id = push(stack);
        try {
            action.execute(id);
        } finally {
            stack.pop();
        }
    }

    @Override
    public void reapply(UserCodeApplicationId id, Runnable runnable) {
        Deque<UserCodeApplicationId> stack = stackThreadLocal.get();
        stack.push(id);
        try {
            runnable.run();
        } finally {
            stack.pop();
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
                Deque<UserCodeApplicationId> stack = stackThreadLocal.get();
                stack.push(id);
                try {
                    action.execute(t);
                } finally {
                    stack.pop();
                }
            }
        };
    }

    @VisibleForTesting
    UserCodeApplicationId push() {
        return push(stackThreadLocal.get());
    }

    @VisibleForTesting
    void pop() {
        stackThreadLocal.get().pop();
    }

    private UserCodeApplicationId push(Deque<UserCodeApplicationId> stack) {
        UserCodeApplicationId id = id();
        stack.push(id);
        return id;
    }

    private static UserCodeApplicationId id() {
        return new UserCodeApplicationId(COUNTER.incrementAndGet());
    }

}
