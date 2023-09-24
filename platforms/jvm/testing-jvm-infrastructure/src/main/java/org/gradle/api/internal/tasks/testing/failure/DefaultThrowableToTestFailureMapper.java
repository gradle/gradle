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

package org.gradle.api.internal.tasks.testing.failure;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.testing.TestFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@NonNullApi
public class DefaultThrowableToTestFailureMapper implements ThrowableToTestFailureMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultThrowableToTestFailureMapper.class);

    private final List<TestFailureMapper> mappers;

    public DefaultThrowableToTestFailureMapper(List<TestFailureMapper> mappers) {
        this.mappers = mappers;
    }

    @Override
    public TestFailure createFailure(Throwable throwable) {
        Throwable currentThrowable = throwable;

        // We recursively dig down through the chain of causes, trying to find a Throwable which we can map to a proper test failure
        while (currentThrowable != null) {
            for (TestFailureMapper mapper : mappers) {
                if (mapper.supports(currentThrowable.getClass())) {
                    try {
                        return mapper.map(currentThrowable, this);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to map supported failure '{}' with mapper '{}': {}", currentThrowable, mapper, ex.getMessage());
                    }
                }
            }
            currentThrowable = currentThrowable.getCause();
        }

        return TestFailure.fromTestFrameworkFailure(throwable);
    }
}
