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

import org.gradle.api.tasks.testing.TestExceptionLogging;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.OutputEventListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

public class TestExceptionLogger extends AbstractTestLogger {
    private final TestExceptionLogging exceptionLogging;

    public TestExceptionLogger(OutputEventListener outputListener, TestExceptionLogging exceptionLogging) {
        super(outputListener);
        this.exceptionLogging = exceptionLogging;
    }

    @Override
    public void afterTest(TestDescriptor descriptor, TestResult result) {
        if (!exceptionLogging.isEnabled() || descriptor.isComposite() || result.getResultType() != TestResult.ResultType.FAILURE) { return; }

        for (Throwable exception : result.getExceptions()) {
            truncateStackTrace(exception, descriptor);
            StringWriter writer = new StringWriter();
            exception.printStackTrace(new PrintWriter(writer));
            println(StyledTextOutput.Style.Normal, "\n" + writer.toString());
        }
    }

    // TODO: mutating exception isn't a good idea, especially not that far down the call chain
    private void truncateStackTrace(Throwable exception, TestDescriptor descriptor) {
        StackTraceElement[] stackTrace = exception.getStackTrace();
        int cutoff = -1;

        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            if (Objects.equal(element.getClassName(), descriptor.getClassName())
                    && Objects.equal(element.getMethodName(), descriptor.getName())) {
                cutoff = i;
                break;
            }
        }

        if (cutoff > -1) {
            List<StackTraceElement> elements = Arrays.asList(stackTrace);
            elements = elements.subList(0, cutoff + 1);
            exception.setStackTrace(elements.toArray(new StackTraceElement[elements.size()]));
        }

        if (exception.getCause() != null) {
            truncateStackTrace(exception.getCause(), descriptor);
        }
    }

    private static class ToStringException extends Exception {
        private ToStringException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
