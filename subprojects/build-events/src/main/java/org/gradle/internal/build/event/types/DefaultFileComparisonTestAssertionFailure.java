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

import org.gradle.api.NonNullApi;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalFileComparisonTestAssertionFailure;

import java.util.Collections;
import java.util.List;

@NonNullApi
public class DefaultFileComparisonTestAssertionFailure extends DefaultTestAssertionFailure implements InternalFileComparisonTestAssertionFailure {

    private final byte[] expectedContent;
    private final byte[] actualContent;

    private DefaultFileComparisonTestAssertionFailure(String message, String description, List<? extends InternalFailure> causes, String expected, String actual, String className, String stacktrace, byte[] expectedContent, byte[] actualContent) {
        super(message, description, causes, expected, actual, className, stacktrace);
        this.expectedContent = expectedContent;
        this.actualContent = actualContent;
    }

    public static DefaultFileComparisonTestAssertionFailure create(
        Throwable t,
        String message,
        String className,
        String stacktrace,
        String expected,
        String actual,
        List<InternalFailure> causes,
        byte[] expectedContent,
        byte[] actualContent
    ) {
        List<InternalFailure> causeFailure;
        if (causes.isEmpty()) {
            Throwable cause = t.getCause();
            causeFailure = cause != null && cause != t ? Collections.singletonList(DefaultFailure.fromThrowable(cause)) : Collections.emptyList();
        } else {
            causeFailure = causes;
        }
        return new DefaultFileComparisonTestAssertionFailure(message, stacktrace, causeFailure, expected, actual, className, stacktrace, expectedContent, actualContent);
    }

    @Override
    public byte[] getExpectedContent() {
        return expectedContent;
    }

    @Override
    public byte[] getActualContent() {
        return actualContent;
    }
}
