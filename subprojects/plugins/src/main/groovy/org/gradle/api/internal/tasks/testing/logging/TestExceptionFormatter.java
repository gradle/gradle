/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging;

import com.google.common.base.Objects;

import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter;
import org.gradle.api.tasks.testing.TestDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class TestExceptionFormatter {
    private final TestLogging testLogging;

    public TestExceptionFormatter(TestLogging testLogging) {
        this.testLogging = testLogging;
    }

    public String format(TestDescriptor descriptor, List<Throwable> exceptions) {
        StringBuilder builder = new StringBuilder();

        for (Throwable exception : exceptions) {
            ExceptionInfo exceptionInfo = toExceptionInfo(exception);
            filterStackTrace(exceptionInfo, descriptor);
            printException(exceptionInfo, builder);
        }

        return builder.toString();
    }

    private ExceptionInfo toExceptionInfo(Throwable exception) {
        if (exception == null) {
            return null;
        }

        ExceptionInfo exceptionInfo = new ExceptionInfo();
        exceptionInfo.message = exception.getMessage();
        exceptionInfo.stackTrace = Arrays.asList(exception.getStackTrace());
        exceptionInfo.cause = toExceptionInfo(exception.getCause());
        return exceptionInfo;
    }

    private void filterStackTrace(ExceptionInfo exceptionInfo, TestDescriptor descriptor) {
        // fast path
        if (testLogging.getStackTraceFilters().contains(TestStackTraceFilter.DISCARD)) {
            exceptionInfo.stackTrace = Collections.emptyList();
            return;
        }

        Spec<StackTraceElement> filter = createCompositeFilter(descriptor);
        List<StackTraceElement> filteredTrace = new ArrayList<StackTraceElement>();

        for (StackTraceElement element : exceptionInfo.stackTrace) {
            if (filter.isSatisfiedBy(element)) {
                filteredTrace.add(element);
            }
        }

        exceptionInfo.stackTrace = filteredTrace;

        if (exceptionInfo.cause != null) {
            filterStackTrace(exceptionInfo.cause, descriptor);
        }
    }

    private void printException(ExceptionInfo exceptionInfo, StringBuilder builder) {
        for (StackTraceElement element : exceptionInfo.stackTrace) {
            builder.append(element);
            builder.append('\n');
        }

        while ((exceptionInfo = exceptionInfo.cause) != null) {
            builder.append("\nCaused by:\n");

            // TODO: check for duplicate elements

            for (StackTraceElement element : exceptionInfo.stackTrace) {
                builder.append(element);
                builder.append('\n');
            }
        }
    }

    private Spec<StackTraceElement> createCompositeFilter(TestDescriptor descriptor) {
        List<Spec<StackTraceElement>> filters = new ArrayList<Spec<StackTraceElement>>();
        for (TestStackTraceFilter type : testLogging.getStackTraceFilters()) {
            filters.add(createFilter(descriptor, type));
        }
        return new AndSpec<StackTraceElement>(filters);
    }

    private Spec<StackTraceElement> createFilter(TestDescriptor descriptor, TestStackTraceFilter type) {
        switch (type) {
            case DISCARD: return Specs.satisfyNone();
            case ENTRY_POINT: return new TestEntryPointStackTraceFilter(descriptor);
            case TRUNCATE: return new TruncatingStackTraceFilter(new TestEntryPointStackTraceFilter(descriptor));
            case GROOVY: return new GroovyStackTraceFilter();
            default: throw new AssertionError();
        }
    }

    private static class TestEntryPointStackTraceFilter implements Spec<StackTraceElement> {
        private final TestDescriptor descriptor;

        TestEntryPointStackTraceFilter(TestDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        public boolean isSatisfiedBy(StackTraceElement element) {
            return Objects.equal(element.getClassName(), descriptor.getClassName())
                    && Objects.equal(element.getMethodName(), descriptor.getName());
        }
    }

    private static class TruncatingStackTraceFilter implements Spec<StackTraceElement> {
        private final Spec<StackTraceElement> truncationPointDetector;
        private boolean truncating;

        private TruncatingStackTraceFilter(Spec<StackTraceElement> truncationPointDetector) {
            this.truncationPointDetector = truncationPointDetector;
        }

        public boolean isSatisfiedBy(StackTraceElement element) {
            if (truncationPointDetector.isSatisfiedBy(element)) {
                truncating = true;
                return true;
            }

            return !truncating;
        }
    }

    // implementation based on Spock's StackTraceFilter class
    private static class GroovyStackTraceFilter implements Spec<StackTraceElement> {
        private static final Pattern INTERNAL_CLASSES = Pattern.compile(
                "org.codehaus.groovy.runtime\\..*"
                        + "|org.codehaus.groovy.reflection\\..*"
                        + "|org.codehaus.groovy\\..*MetaClass.*"
                        + "|groovy\\..*MetaClass.*"
                        + "|groovy.lang.MetaMethod"
                        + "|java.lang.reflect\\..*"
                        + "|sun.reflect\\..*"
        );

        public boolean isSatisfiedBy(StackTraceElement element) {
            return !isInternalClass(element) && !isGeneratedMethod(element);
        }

        private boolean isInternalClass(StackTraceElement element) {
            return INTERNAL_CLASSES.matcher(element.getClassName()).matches();
        }

        private boolean isGeneratedMethod(StackTraceElement element) {
            return element.getLineNumber() < 0;
        }
    }

    private static class ExceptionInfo {
        String message;
        List<StackTraceElement> stackTrace;
        ExceptionInfo cause;
    }
}
