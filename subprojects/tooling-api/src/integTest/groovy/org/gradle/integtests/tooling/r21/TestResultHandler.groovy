/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r21

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TestResultHandler implements ResultHandler<Object> {
    final latch = new CountDownLatch(1)
    final boolean expectFailure
    def failure

    TestResultHandler() {
        this(true)
    }

    TestResultHandler(boolean expectFailure) {
        this.expectFailure = expectFailure
    }

    void onComplete(Object result) {
        latch.countDown()
    }

    void onFailure(GradleConnectionException failure) {
        this.failure = failure
        latch.countDown()
    }

    def finished() {
        finished(10)
    }

    def finished(int seconds) {
        latch.await(seconds, TimeUnit.SECONDS)
        if (expectFailure) {
            assert failure != null
        } else {
            if (failure != null) {
                throw failure
            }
        }
    }
}
