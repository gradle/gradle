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

import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestFailureDetails;
import org.gradle.internal.serialize.PlaceholderExceptionSupport;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

public class DefaultTestFailure extends TestFailure {

    private final Throwable rawFailure;
    private final TestFailureDetails details;

    private final List<TestFailure> causes;

    public DefaultTestFailure(Throwable rawFailure, TestFailureDetails details, List<TestFailure> causes) {
        this.rawFailure = rawFailure;
        this.details = details;
        this.causes = causes;
    }

    @Override
    public Throwable getRawFailure() {
        return rawFailure;
    }

    @Override
    public TestFailureDetails getDetails() {
        return details;
    }

    @Override
    public List<TestFailure> getCauses() {
        return causes;
    }

    @Override
    public String toString() {
        return "test failure {" +
            "rawFailure=" + rawFailure.getClass().getCanonicalName() +
            ", causes=" + causes.size() +
            ", details=" + details +
            '}';
    }

    public static TestFailure fromTestAssumptionFailure(Throwable failure) {
        TestFailureDetails details = new AssumptionFailureDetails(messageOf(failure), classNameOf(failure), stacktraceOf(failure));
        return new DefaultTestFailure(failure, details, Collections.<TestFailure>emptyList());
    }

    public static TestFailure fromTestAssertionFailure(Throwable failure, String expected, String actual, @Nullable List<TestFailure> causes) {
        TestFailureDetails details = new AssertionFailureDetails(messageOf(failure), classNameOf(failure), stacktraceOf(failure), expected, actual);
        return new DefaultTestFailure(failure, details, emptyIfNull(causes));
    }

    public static TestFailure fromFileComparisonTestAssertionFailure(Throwable failure, String expected, String actual, @Nullable List<TestFailure> causes, byte[] expectedContent, byte[] actualContent) {
        TestFailureDetails details = new FileComparisonFailureDetails(messageOf(failure), classNameOf(failure), stacktraceOf(failure), expected, actual, expectedContent, actualContent);
        return new DefaultTestFailure(failure, details, emptyIfNull(causes));
    }

    public static TestFailure fromTestFrameworkFailure(Throwable failure, @Nullable List<TestFailure> causes) {
        TestFailureDetails details = new DefaultTestFailureDetails(messageOf(failure), classNameOf(failure), stacktraceOf(failure));
        return new DefaultTestFailure(failure, details, emptyIfNull(causes));
    }

    private static List<TestFailure> emptyIfNull(@Nullable List<TestFailure> causes) {
        return causes == null ? Collections.<TestFailure>emptyList() : causes;
    }

    private static String messageOf(Throwable throwable) {
        try {
            return throwable.getMessage();
        } catch (Throwable t) {
            return String.format("Could not determine failure message for exception of type %s: %s", classNameOf(throwable), t);
        }
    }

    private static String classNameOf(Throwable failure) {
        return failure instanceof PlaceholderExceptionSupport
            ? ((PlaceholderExceptionSupport) failure).getExceptionClassName()
            : failure.getClass().getName();
    }

    private static String stacktraceOf(Throwable throwable) {
        try {
            StringWriter out = new StringWriter();
            PrintWriter wrt = new PrintWriter(out);
            throwable.printStackTrace(wrt);
            return out.toString();
        } catch (Exception t) {
            return stacktraceOf(t);
        }
    }

}
