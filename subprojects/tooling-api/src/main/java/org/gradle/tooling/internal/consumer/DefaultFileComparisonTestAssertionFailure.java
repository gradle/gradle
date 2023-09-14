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

import org.gradle.api.NonNullApi;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FileComparisonTestAssertionFailure;

import javax.annotation.Nullable;
import java.util.List;

@NonNullApi
public class DefaultFileComparisonTestAssertionFailure extends DefaultTestAssertionFailure implements FileComparisonTestAssertionFailure {

    private final byte[] expectedContent;
    private final byte[] actualContent;

    public DefaultFileComparisonTestAssertionFailure(String message, String description, String expected, String actual, List<? extends Failure> causes, String className, String stacktrace, byte[] expectedContent, byte[] actualContent) {
        super(message, description, expected, actual, causes, className, stacktrace);
        this.expectedContent = expectedContent;
        this.actualContent = actualContent;
    }

    @Nullable
    @Override
    public byte[] getExpectedContent() {
        return expectedContent;
    }

    @Nullable
    @Override
    public byte[] getActualContent() {
        return actualContent;
    }
}
