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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.Incubating;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.exceptions.StyledException;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of merging multiple {@link TestSuiteVerificationException}s into a
 * single exception for reporting to the console
 *
 * <strong>Not for external use.</strong>
 * @since 7.6
 */
@Incubating
public final class MergedTestSuiteFailuresException extends StyledException implements MultiCauseException {
    private final List<TestSuiteVerificationException> exceptions;

    public MergedTestSuiteFailuresException(String message, List<TestSuiteVerificationException> exceptions) {
        super(message);
        this.exceptions = new ArrayList<TestSuiteVerificationException>(exceptions);
        setStackTrace(new StackTraceElement[0]);
    }

    @Override
    public List<TestSuiteVerificationException> getCauses() {
        return exceptions;
    }
}
