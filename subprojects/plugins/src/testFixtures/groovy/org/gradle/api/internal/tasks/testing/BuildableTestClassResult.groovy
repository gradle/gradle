/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing

import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult
import org.gradle.util.ConfigureUtil

class BuildableTestClassResult extends TestClassResult {
    List<MethodTestOutputEvent> outputEvents = []

    long duration = 1000

    BuildableTestClassResult(String className, long startTime = 0) {
        super(className, startTime)
    }

    BuildableTestMethodResult testcase(String name, Closure configClosure = {}) {
        testcase(name, 1000, configClosure)
    }

    BuildableTestMethodResult testcase(String name, long endTime, Closure configClosure = {}) {
        BuildableTestMethodResult methodResult = new BuildableTestMethodResult(name, outputEvents, new SimpleTestResult(endTime))
        add(methodResult)
        ConfigureUtil.configure(configClosure, methodResult)
    }

    @Override
    long getDuration() {
        this.duration
    }


}
