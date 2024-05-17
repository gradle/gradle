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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultTestFailure that = (DefaultTestFailure) o;

        if (rawFailure != null ? !rawFailure.equals(that.rawFailure) : that.rawFailure != null) {
            return false;
        }
        return details != null ? details.equals(that.details) : that.details == null;
    }

    @Override
    public int hashCode() {
        int result = rawFailure != null ? rawFailure.hashCode() : 0;
        result = 31 * result + (details != null ? details.hashCode() : 0);
        return result;
    }

    public static TestFailure fromTestAssertionFailure(Throwable failure, String expected, String actual, List<TestFailure> causes) {
        DefaultTestFailureDetails details = new DefaultTestFailureDetails(messageOf(failure), classNameOf(failure), stacktraceOf(failure), true, false, expected, actual, null, null);
        return new DefaultTestFailure(failure, details, causes == null ? Collections.<TestFailure>emptyList() : causes);
    }

    public static TestFailure fromFileComparisonTestAssertionFailure(Throwable failure, String expected, String actual, List<TestFailure> causes, byte[] expectedContent, byte[] actualContent) {
        DefaultTestFailureDetails details = new DefaultTestFailureDetails(messageOf(failure), classNameOf(failure), stacktraceOf(failure), true, true, expected, actual, expectedContent, actualContent);
        return new DefaultTestFailure(failure, details, causes == null ? Collections.<TestFailure>emptyList() : causes);
    }

    public static TestFailure fromTestFrameworkFailure(Throwable failure, List<TestFailure> causes) {
        DefaultTestFailureDetails details = new DefaultTestFailureDetails(messageOf(failure), classNameOf(failure), stacktraceOf(failure), false, false, null, null, null, null);
        return new DefaultTestFailure(failure, details, causes == null ? Collections.<TestFailure>emptyList() : causes);
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
