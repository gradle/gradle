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

package org.gradle.integtests

import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

//this class just holds onto our liveOutput and also tracks whether or not we've finished.
public class TestExecutionInteraction implements ExecuteGradleCommandServerProtocol.ExecutionInteraction {
    private StringBuilder liveOutput = new StringBuilder();
    public boolean executionFinishedReported = false;
    public boolean wasSuccessful = false;
    public String finalMessage;
    private Throwable failure
    private final Lock lock = new ReentrantLock()
    private final Condition condition = lock.newCondition()

    public void reportLiveOutput(String message) {
        liveOutput.append(message);
    }

    //when we finish executing, we'll make sure we got some type of live output from gradle.

    public void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable) {
        lock.lock()
        try {
            executionFinishedReported = true
            this.wasSuccessful = wasSuccessful
            this.finalMessage = message
            failure = throwable
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    def assertCompleted() {
        lock.lock()
        try {
            if (!executionFinishedReported) {
                throw new AssertionError("Request has not completed.")
            }
        } finally {
            lock.unlock()
        }
    }

    public waitForCompletion(int maxWaitValue, TimeUnit maxWaitUnits) {
        Date expiry = new Date(System.currentTimeMillis() + maxWaitUnits.toMillis(maxWaitValue))
        lock.lock()
        try {
            while (!executionFinishedReported) {
                if (!condition.awaitUntil(expiry)) {
                    throw new AssertionError("Timeout waiting for execution to complete.")
                }
            }
            if (failure != null) {
                throw failure
            }
        } finally {
            lock.unlock()
        }
    }

    public void reportExecutionStarted() {}

    public void reportNumberOfTasksToExecute(int size) {}

    public void reportTaskStarted(String message, float percentComplete) {}

    public void reportTaskComplete(String message, float percentComplete) {}


}
