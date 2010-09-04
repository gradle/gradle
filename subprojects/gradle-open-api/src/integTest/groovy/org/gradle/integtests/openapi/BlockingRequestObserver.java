/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests.openapi;

import org.gradle.openapi.external.foundation.RequestObserverVersion1;
import org.gradle.openapi.external.foundation.RequestVersion1;
import org.gradle.util.UncheckedException;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingRequestObserver implements RequestObserverVersion1 {
    private final String typeOfInterest;  //either RequestVersion1.EXECUTION_TYPE or RequestVersion1.REFRESH_TYPE
    private RequestVersion1 request;
    private Integer result;
    private String output;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Throwable failure;

    public BlockingRequestObserver() {
        this(RequestVersion1.EXECUTION_TYPE);
    }

    public BlockingRequestObserver(String typeOfInterest) {
        this.typeOfInterest = typeOfInterest;
    }

    public RequestVersion1 getRequest() {
        lock.lock();
        try {
            assertComplete();
            return request;
        } finally {
            lock.unlock();
        }
    }

    public int getResult() {
        lock.lock();
        try {
            assertComplete();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public String getOutput() {
        lock.lock();
        try {
            assertComplete();
            return output;
        } finally {
            lock.unlock();
        }
    }

    private void assertComplete() {
        if (request == null) {
            throw new AssertionError("Request has not completed.");
        }
    }

    void reset() {
        lock.lock();
        try {
            request = null;
            result = null;
            output = null;
        } finally {
            lock.unlock();
        }
    }

    public void executionRequestAdded(RequestVersion1 request) {
    }

    public void refreshRequestAdded(RequestVersion1 request) {
    }

    public void aboutToExecuteRequest(RequestVersion1 request) {
    }

    public void requestExecutionComplete(RequestVersion1 request, int result, String output) {
        if (this.typeOfInterest.equals(request.getType())) {
            lock.lock();
            try {
                if (this.request != null) {
                    failure = new AssertionError("Multiple results for request.");
                }
                this.request = request;
                this.result = result;
                this.output = output;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    void waitForRequestExecutionComplete(int timeOutValue, TimeUnit timeOutUnits) {
        lock.lock();
        try {
            Date expiry = new Date(System.currentTimeMillis() + timeOutUnits.toMillis(timeOutValue));
            while (failure == null && request == null) {
                if (!condition.awaitUntil(expiry)) {
                    throw new AssertionError("Timeout waiting for request to complete.");
                }
            }
            if (failure != null) {
                throw failure;
            }
        } catch(Throwable t) {
            throw UncheckedException.asUncheckedException(t);
        } finally {
            lock.unlock();
        }
    }
}
