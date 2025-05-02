/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.userinput;

import org.gradle.internal.UncheckedException;

public class DefaultUserInputReader implements UserInputReader {
    private final Object lock = new Object();
    private UserInput pending;
    private boolean finished;

    @Override
    public void startInput() {
        synchronized (lock) {
            pending = null;
            finished = false;
        }
    }

    @Override
    public void putInput(UserInput input) {
        synchronized (lock) {
            if (input == END_OF_INPUT) {
                finished = true;
                lock.notifyAll();
            } else {
                if (pending != null) {
                    throw new IllegalStateException("Multiple responses received.");
                }
                if (finished) {
                    throw new IllegalStateException("Response received after input closed.");
                }
                pending = input;
                lock.notifyAll();
            }
        }
    }

    @Override
    public UserInput readInput() {
        synchronized (lock) {
            while (!finished && pending == null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (pending != null) {
                UserInput result = pending;
                pending = null;
                return result;
            }

            return END_OF_INPUT;
        }
    }
}
