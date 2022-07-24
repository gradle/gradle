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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import java.util.List;

public class FullExceptionFormatter implements TestExceptionFormatter {
    private static final String INDENT = "    ";

    private final TestLogging testLogging;

    public FullExceptionFormatter(TestLogging testLogging) {
        this.testLogging = testLogging;
    }

    @Override
    public String format(TestDescriptor descriptor, List<Throwable> exceptions) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < exceptions.size(); i++) {
            printException(descriptor, exceptions.get(i), null, 0, builder);
            if (i < exceptions.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private void printException(TestDescriptor descriptor, Throwable exception,
                                @Nullable List<StackTraceElement> parentTrace, int exceptionLevel, StringBuilder builder) {
        String exceptionIndent = Strings.repeat(INDENT, exceptionLevel + 1);
        String exceptionText = exceptionLevel == 0 ? exception.toString() : "\nCaused by:\n" + exception.toString();
        String indentedText = TextUtil.indent(exceptionText, exceptionIndent);
        builder.append(indentedText);
        builder.append('\n');

        String stackTraceIndent = exceptionIndent + INDENT;
        List<StackTraceElement> stackTrace = null;

        if (testLogging.getShowStackTraces()) {
            stackTrace = filterStackTrace(exception, descriptor);
            int commonElements = countCommonElements(stackTrace, parentTrace);
            for (int i = 0; i < stackTrace.size() - commonElements; i++) {
                builder.append(stackTraceIndent);
                builder.append("at ");
                builder.append(stackTrace.get(i));
                builder.append('\n');
            }
            if (commonElements != 0) {
                builder.append(stackTraceIndent);
                builder.append("... ");
                builder.append(commonElements);
                builder.append(" more");
                builder.append('\n');
            }
        }

        if (testLogging.getShowCauses() && exception.getCause() != null) {
            printException(descriptor, exception.getCause(), stackTrace, exceptionLevel + 1, builder);
        }
    }

    private List<StackTraceElement> filterStackTrace(Throwable exception, TestDescriptor descriptor) {
        Spec<StackTraceElement> filterSpec = createCompositeFilter(descriptor);
        StackTraceFilter filter = new StackTraceFilter(filterSpec);
        return filter.filter(exception);
    }

    private Spec<StackTraceElement> createCompositeFilter(TestDescriptor descriptor) {
        List<Spec<StackTraceElement>> filters = Lists.newArrayList();
        for (TestStackTraceFilter type : testLogging.getStackTraceFilters()) {
            filters.add(createFilter(descriptor, type));
        }
        return new AndSpec<StackTraceElement>(filters);
    }

    private Spec<StackTraceElement> createFilter(TestDescriptor descriptor, TestStackTraceFilter filterType) {
        switch (filterType) {
            case ENTRY_POINT:
                return new ClassMethodNameStackTraceSpec(descriptor.getClassName(), descriptor.getName());
            case TRUNCATE:
                return new TruncatedStackTraceSpec(new ClassMethodNameStackTraceSpec(descriptor.getClassName(), null));
            case GROOVY:
                return new GroovyStackTraceSpec();
            default:
                throw new AssertionError();
        }
    }

    private int countCommonElements(List<StackTraceElement> stackTrace, @Nullable List<StackTraceElement> parentTrace) {
        if (parentTrace == null) {
            return 0;
        }

        int commonElements = 0;
        for (int i = stackTrace.size() - 1, j = parentTrace.size() - 1;
            // i >= 1 makes sure that commonElements < stackTrace.size()
             i >= 1 && j >= 0 && stackTrace.get(i).equals(parentTrace.get(j)); i--, j--) {
            commonElements++;
        }
        return commonElements;
    }
}
