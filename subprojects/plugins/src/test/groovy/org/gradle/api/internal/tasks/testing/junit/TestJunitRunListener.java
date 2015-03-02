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

package org.gradle.api.internal.tasks.testing.junit;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class TestJunitRunListener extends RunListener {

    public static RunListener mockRunListener;

    public TestJunitRunListener() {
        super();
        assert mockRunListener != null;
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        mockRunListener.testRunStarted(description);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        mockRunListener.testRunFinished(result);
    }

    @Override
    public void testStarted(Description description) throws Exception {
        mockRunListener.testStarted(description);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        mockRunListener.testFinished(description);
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        mockRunListener.testFailure(failure);
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        mockRunListener.testAssumptionFailure(failure);
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        mockRunListener.testIgnored(description);
    }
}
