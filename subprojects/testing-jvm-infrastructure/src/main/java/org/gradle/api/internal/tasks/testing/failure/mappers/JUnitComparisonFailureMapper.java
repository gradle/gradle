/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.failure.mappers;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.failure.FailureMapper;
import org.gradle.api.internal.tasks.testing.failure.ThrowableToFailureMapper;
import org.gradle.api.tasks.testing.TestFailure;

import java.util.Arrays;
import java.util.List;

@NonNullApi
public class JUnitComparisonFailureMapper extends FailureMapper {
    @Override
    protected List<String> getSupportedClassNames() {
        return Arrays.asList(
            "junit.framework.ComparisonFailure",
            "org.junit.ComparisonFailure"
        );
    }

    @Override
    public TestFailure map(Throwable throwable, ThrowableToFailureMapper mapper) throws Exception {
        String expectedValue = invokeMethod(throwable, "getExpected", String.class);
        String actualValue = invokeMethod(throwable, "getActual", String.class);

        return TestFailure.fromTestAssertionFailure(throwable, expectedValue, actualValue);
    }
}
