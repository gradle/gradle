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
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.internal.serialize.PlaceholderExceptionSupport;

import java.util.List;

public class ShortExceptionFormatter implements TestExceptionFormatter {
    private static final String INDENT = "    ";

    private final TestLogging testLogging;

    public ShortExceptionFormatter(TestLogging testLogging) {
        this.testLogging = testLogging;
    }

    @Override
    public String format(TestDescriptor descriptor, List<Throwable> exceptions) {
        StringBuilder builder = new StringBuilder();
        for (Throwable exception : exceptions) {
            printException(descriptor, exception, false, 1, builder);
        }
        return builder.toString();
    }

    private void printException(TestDescriptor descriptor, Throwable exception, boolean cause, int indentLevel, StringBuilder builder) {
        String indent = Strings.repeat(INDENT, indentLevel);
        builder.append(indent);
        if (cause) {
            builder.append("Caused by: ");
        }
        String className = exception instanceof PlaceholderExceptionSupport
                ? ((PlaceholderExceptionSupport) exception).getExceptionClassName() : exception.getClass().getName();
        builder.append(className);

        StackTraceFilter filter = new StackTraceFilter(new ClassMethodNameStackTraceSpec(descriptor.getClassName(), null));
        List<StackTraceElement> stackTrace = filter.filter(exception);
        if (stackTrace.size() > 0) {
            StackTraceElement element = stackTrace.get(0);
            builder.append(" at ");
            builder.append(element.getFileName());
            builder.append(':');
            builder.append(element.getLineNumber());
        }
        builder.append('\n');

        if (testLogging.getShowCauses() && exception.getCause() != null) {
            printException(descriptor, exception.getCause(), true, indentLevel + 1, builder);
        }
    }
}
