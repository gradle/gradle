/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.tasks.testing.TestResult;

/**
 * by Szczepan Faber, created at: 11/13/12
 */
public class TestMethodResult {

    public final String name;
    public final TestResult result;

    public TestMethodResult(String name, TestResult result) {
        this.name = name;
        this.result = result;
    }

    public long getDuration() {
        return result.getEndTime() - result.getStartTime();
    }

    public long getEndTime() {
        return result.getEndTime();
    }
}