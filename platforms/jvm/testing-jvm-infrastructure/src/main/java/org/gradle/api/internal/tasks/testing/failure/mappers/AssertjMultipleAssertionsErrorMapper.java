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

import org.gradle.api.internal.tasks.testing.failure.TestFailureMapper;
import org.gradle.api.internal.tasks.testing.failure.ThrowableToTestFailureMapper;
import org.gradle.api.tasks.testing.TestFailure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps {@code org.assertj.core.error.MultipleAssertionsError} to {@link TestFailure}.
 * <p>
 * See {@link TestFailureMapper} for more details about failure mapping.
 */
public class AssertjMultipleAssertionsErrorMapper extends TestFailureMapper {

    @Override
    protected List<String> getSupportedClassNames() {
        return Collections.singletonList(
            "org.assertj.core.error.MultipleAssertionsError"
        );
    }

    @Override
    public TestFailure map(Throwable throwable, ThrowableToTestFailureMapper rootMapper) throws Exception {
        return TestFailure.fromTestAssertionFailure(throwable, null, null, mapInnerFailures(throwable, rootMapper));
    }

    @SuppressWarnings("unchecked")
    protected static List<TestFailure> mapInnerFailures(Throwable throwable, ThrowableToTestFailureMapper mapper) throws Exception {
        List<TestFailure> failures = new ArrayList<TestFailure>();

        List<Throwable> innerExceptions = invokeMethod(throwable, "getErrors", List.class);
        for (Throwable innerException : innerExceptions) {
            failures.add(mapper.createFailure(innerException));
        }

        return failures;
    }

}
