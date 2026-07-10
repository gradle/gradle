/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalTestAssertionFailure;

import java.util.Collections;
import java.util.List;

public class DefaultTestAssertionFailure extends AbstractTestFailure implements InternalTestAssertionFailure {

    private final String expected;
    private final String actual;

    DefaultTestAssertionFailure(String message, String description, List<? extends InternalFailure> causes, String expected, String actual, String className, String stacktrace) {
        super(message, description, causes, className, stacktrace);
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public String getExpected() {
        return expected;
    }

    @Override
    public String getActual() {
        return actual;
    }

    public static DefaultTestAssertionFailure create(Throwable t, String message, String className, String stacktrace, String expected, String actual, List<InternalFailure> causes) {
        List<InternalFailure> causeFailure;
        if (causes.isEmpty()) {
            Throwable cause = t.getCause();
            causeFailure = cause != null && cause != t ? Collections.singletonList(DefaultFailure.fromThrowable(cause)) : Collections.emptyList();
        } else {
            causeFailure = causes;
        }
        return new DefaultTestAssertionFailure(message, stacktrace, causeFailure, expected, actual, className, stacktrace);
    }
}
