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
import java.util.ArrayDeque;
import java.util.Deque;

public class TestUserCodeApplicationContext implements UserCodeApplicationContext {
    private long current = 0;
    private final Deque<UserCodeApplicationId> stack = new ArrayDeque<>();

    @Override
    public void apply(DisplayName displayName, Action<? super UserCodeApplicationId> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reapply(UserCodeApplicationId id, Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> Action<T> decorateWithCurrent(Action<T> action) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public UserCodeApplicationId current() {
        return stack.peekFirst();
    }

    @Nullable
    @Override
    public DisplayName currentDisplayName() {
        throw new UnsupportedOperationException();
    }

    public UserCodeApplicationId push() {
        UserCodeApplicationId id = new UserCodeApplicationId(current++);
        stack.push(id);
        return id;
    }

    public void pop() {
        stack.pop();
    }

}
