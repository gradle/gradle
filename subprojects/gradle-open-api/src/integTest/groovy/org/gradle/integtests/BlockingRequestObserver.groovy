/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.openapi.external.foundation.RequestObserverVersion1
import org.gradle.openapi.external.foundation.RequestVersion1
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Condition
import junit.framework.AssertionFailedError

public class BlockingRequestObserver implements RequestObserverVersion1 {
    private String typeOfInterest;  //either RequestVersion1.EXECUTION_TYPE or RequestVersion1.REFRESH_TYPE
    private RequestVersion1 request
    private Integer result
    private String output
    private final Lock lock = new ReentrantLock()
    private final Condition condition = lock.newCondition()
    private Throwable failure

    def BlockingRequestObserver(typeOfInterest) {
        this.typeOfInterest = typeOfInterest;
    }

    void executionRequestAdded(RequestVersion1 request) { }

    void refreshRequestAdded(RequestVersion1 request) { }

    void aboutToExecuteRequest(RequestVersion1 request) { }

    void requestExecutionComplete(RequestVersion1 request, int result, String output) {
        //refreshes will come through here. We're ignoring those
        if (this.typeOfInterest.equals(request.getType())) {
            lock.lock()
            try {
                if (this.request) {
                    failure = new AssertionFailedError("Multiple results for request.")
                }
                this.request = request
                this.result = result
                this.output = output
                condition.signalAll()
            }
            finally {
                lock.unlock()
            }
        }
    }

    void waitForRequestExecutionComplete() {
        lock.lock()
        try {
            Date expiry = new Date(System.currentTimeMillis() + 20000)
            while (failure == null && !request) {
                if (!condition.awaitUntil(expiry)) {
                    throw new AssertionFailedError("Timeout waiting for request to complete.")
                }
            }
            if (failure) {
                throw failure
            }
        } finally {
            lock.unlock()
        }
    }
}
