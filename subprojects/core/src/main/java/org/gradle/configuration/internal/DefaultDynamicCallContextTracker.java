/*
 * Copyright 2022 the original author or authors.
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

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DefaultDynamicCallContextTracker implements DynamicCallContextTracker {
    private static class State {
        Stack<Object> entryPointsStack = new Stack<>();
    }

    private final ThreadLocal<State> stateByThread = ThreadLocal.withInitial(State::new);
    private final List<Consumer<Object>> enterListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Object>> leaveListeners = new CopyOnWriteArrayList<>();

    @Override
    public void enterDynamicCall(@Nonnull Object entryPoint) {
        currentEntryPointStack().push(entryPoint);
        enterListeners.forEach(listener -> listener.accept(entryPoint));
    }

    @Override
    public void leaveDynamicCall(@Nonnull Object entryPoint) {
        Stack<Object> entryPointsStack = currentEntryPointStack();
        Object top = entryPointsStack.peek();
        if (top != entryPoint) {
            throw new IllegalStateException(
                "Mismatch in leaving dynamic call: leaving " + entryPoint + ", while " + top + " should be left."
            );
        }
        entryPointsStack.pop();
        leaveListeners.forEach(listener -> listener.accept(entryPoint));
    }

    @Override
    public void onEnter(Consumer<Object> listener) {
        enterListeners.add(listener);
    }

    @Override
    public void onLeave(Consumer<Object> listener) {
        leaveListeners.add(listener);
    }

    private Stack<Object> currentEntryPointStack() {
        return stateByThread.get().entryPointsStack;
    }
}
