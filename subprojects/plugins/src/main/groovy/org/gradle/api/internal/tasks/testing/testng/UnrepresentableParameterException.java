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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.GradleException;
import org.testng.ITestResult;

public class UnrepresentableParameterException extends GradleException {

    public UnrepresentableParameterException(ITestResult iTestResult, int paramIndex, Throwable cause) {
        super(
                String.format("Parameter %s of iteration %s of method '%s' toString() method threw exception",
                        paramIndex + 1, iTestResult.getMethod().getCurrentInvocationCount() + 1, iTestResult.getName()
                ),
                cause
        );
    }
}
