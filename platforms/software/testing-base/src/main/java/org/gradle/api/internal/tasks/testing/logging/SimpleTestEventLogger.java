/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestFailureDetails;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.util.internal.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Console logger for test events for non-composite (atomic) failed tests.
 *
 * This logger is non-configurable.
 */
@NonNullApi
public class SimpleTestEventLogger implements TestListenerInternal {
    private final StyledTextOutputFactory textOutputFactory;

    public SimpleTestEventLogger(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        // ignored
    }

    @Override
    public void completed(TestDescriptorInternal descriptor, TestResult result, TestCompleteEvent completeEvent) {

        // Only rendering the final test descriptors
        if (!descriptor.isComposite()) {
            if (result.getResultType() == TestResult.ResultType.FAILURE) {
                StyledTextOutput output = textOutputFactory.create(SimpleTestEventLogger.class);

                // Print header with path to descriptor
                output.println().append(toEventPath(descriptor)).append(" ");

                // Print the result
                output.withStyle(StyledTextOutput.Style.Failure).println("FAILED");

                // Print the failure message(s)
                for (TestFailure failure : result.getFailures()) {
                    TestFailureDetails details = failure.getDetails();
                    if (!TextUtil.isBlank(details.getMessage())) {
                        if (details.isAssertionFailure()) {
                            // test assertion (should be most common)
                            output.append("    ").withStyle(StyledTextOutput.Style.Failure).println(details.getMessage());
                        } else if (details.isFileComparisonFailure()) {
                            // comparison failure
                            output.append("    Expected: ").withStyle(StyledTextOutput.Style.Failure).println(details.getExpected());
                            output.append("    Actual: ").withStyle(StyledTextOutput.Style.Success).println(details.getActual());
                        } else {
                            // test framework failure?
                            output.append("    ").withStyle(StyledTextOutput.Style.Identifier).append(details.getClassName());
                            output.append(": ").println(details.getMessage());
                        }
                    }
                }
            }
        } else if (descriptor.getParent() == null) {
            // print the result of the root most group
            StyledTextOutput output = textOutputFactory.create(SimpleTestEventLogger.class);
            output.println().formatln("%d tests completed, %d succeeded, %d failed", result.getTestCount(), result.getSuccessfulTestCount(), result.getFailedTestCount());
        }
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        // ignored
    }

    private String toEventPath(TestDescriptor descriptor) {
        List<String> names = new ArrayList<>();
        TestDescriptor current = descriptor;
        while (current != null) {
            names.add(current.getDisplayName());
            current = current.getParent();
        }
        return Joiner.on(" > ").join(Lists.reverse(names));
    }
}
