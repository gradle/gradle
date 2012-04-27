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

package org.gradle.api.internal.tasks.testing.results;

import com.google.common.base.Objects;

import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.testing.StackTraceFilter;
import org.gradle.api.tasks.testing.TestExceptionLogging;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.OutputEventListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TestExceptionLogger extends AbstractTestLogger {
    private final TestExceptionLogging exceptionLogging;

    public TestExceptionLogger(OutputEventListener outputListener, TestExceptionLogging exceptionLogging) {
        super(outputListener);
        this.exceptionLogging = exceptionLogging;
    }

    @Override
    public void afterTest(TestDescriptor descriptor, TestResult result) {
        if (!exceptionLogging.isEnabled()) { return; }

        for (Throwable exception : result.getExceptions()) {
            filterStackTrace(exception, descriptor);
            StringWriter writer = new StringWriter();
            exception.printStackTrace(new PrintWriter(writer));
            println(StyledTextOutput.Style.Normal, "\n" + writer.toString());
        }
    }

    // TODO: mutating exception isn't a good idea, especially not that far down the call chain
    // probably we should replicate the printStackTrace code instead of using it directly
    private void filterStackTrace(Throwable exception, TestDescriptor descriptor) {
        Spec<StackTraceElement> filter = createCompositeFilter(descriptor);
        List<StackTraceElement> filteredTrace = new ArrayList<StackTraceElement>();

        for (StackTraceElement element : exception.getStackTrace()) {
            if (filter.isSatisfiedBy(element)) {
                filteredTrace.add(element);
            }
        }

        exception.setStackTrace(filteredTrace.toArray(new StackTraceElement[filteredTrace.size()]));

        if (exception.getCause() != null) {
            filterStackTrace(exception.getCause(), descriptor);
        }
    }

    private Spec<StackTraceElement> createCompositeFilter(TestDescriptor descriptor) {
        List<Spec<StackTraceElement>> filters = new ArrayList<Spec<StackTraceElement>>();
        for (StackTraceFilter type : exceptionLogging.getStackTraceFilters()) {
            filters.add(createFilter(descriptor, type));
        }
        return new AndSpec<StackTraceElement>(filters);
    }

    private Spec<StackTraceElement> createFilter(TestDescriptor descriptor, StackTraceFilter type) {
        switch (type) {
            case HIDE: return Specs.satisfyNone();
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

    // implementation inspired from Spock's StackTraceFilter class
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
}
