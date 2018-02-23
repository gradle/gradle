/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JUnitTestEventAdapter extends RunListener {
    private static final Pattern DESCRIPTOR_PATTERN = Pattern.compile("(.*)\\((.*)\\)(\\[\\d+])?", Pattern.DOTALL);
    private final IdGenerator<?> idGenerator;
    private final GenericJUnitTestEventAdapter<Description> adapter;

    public JUnitTestEventAdapter(TestResultProcessor resultProcessor, Clock clock,
                                 IdGenerator<?> idGenerator) {
        this.idGenerator = idGenerator;
        this.adapter = new GenericJUnitTestEventAdapter<Description>(resultProcessor, clock);
    }

    @Override
    public void testStarted(Description description) throws Exception {
        adapter.testStarted(description, nullSafeDescriptor(idGenerator.generateId(), description));
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        adapter.testFailure(failure.getDescription(), nullSafeDescriptor(idGenerator.generateId(), failure.getDescription()), failure.getException());
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        adapter.testAssumptionFailure(failure.getDescription());
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        if (methodName(description) == null) {
            // An @Ignored class, ignore the event. We don't get testIgnored events for each method, so we have
            // generate them on our own
            processIgnoredClass(description);
        } else {
            adapter.testIgnored(descriptor(idGenerator.generateId(), description));
        }
    }

    private void processIgnoredClass(Description description) throws Exception {
        IgnoredTestDescriptorProvider provider = new IgnoredTestDescriptorProvider();
        String className = className(description);
        for (Description childDescription : provider.getAllDescriptions(description, className)) {
            testIgnored(childDescription);
        }
    }

    @Override
    public void testFinished(Description description) throws Exception {
        adapter.testFinished(description);
    }

    private TestDescriptorInternal descriptor(Object id, Description description) {
        return new DefaultTestDescriptor(id, className(description), methodName(description));
    }

    private TestDescriptorInternal nullSafeDescriptor(Object id, Description description) {
        String methodName = methodName(description);
        if (methodName != null) {
            return new DefaultTestDescriptor(id, className(description), methodName);
        } else {
            return new DefaultTestDescriptor(id, className(description), "classMethod");
        }
    }

    // Use this instead of Description.getMethodName(), it is not available in JUnit <= 4.5
    public static String methodName(Description description) {
        return methodName(description.toString());
    }

    public static String methodName(String description) {
        Matcher matcher = methodStringMatcher(description);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    // Use this instead of Description.getClassName(), it is not available in JUnit <= 4.5
    public static String className(Description description) {
        return className(description.toString());
    }

    public static String className(String description) {
        Matcher matcher = methodStringMatcher(description);
        return matcher.matches() ? matcher.group(2) : description;
    }

    private static Matcher methodStringMatcher(String description) {
        return DESCRIPTOR_PATTERN.matcher(description);
    }

}

