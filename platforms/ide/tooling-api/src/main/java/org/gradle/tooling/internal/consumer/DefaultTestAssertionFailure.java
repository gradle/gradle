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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.Failure;
import org.gradle.tooling.TestAssertionFailure;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultTestAssertionFailure extends DefaultFailure implements TestAssertionFailure {
    private final String expected;
    private final String actual;
    private final String stacktrace;
    private final String className;

    public DefaultTestAssertionFailure(@Nullable Class<? extends Throwable> exceptionType, String message, String description, String expected, String actual, List<? extends Failure> causes, String className, String stacktrace) {
        super(exceptionType, message, description, causes);
        this.expected = expected;
        this.actual = actual;
        this.className = className;
        this.stacktrace = stacktrace;
    }

    @Override
    public String getExpected() {
        return expected;
    }

    @Override
    public String getActual() {
        return actual;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getStacktrace() {
        return stacktrace;
    }
}
