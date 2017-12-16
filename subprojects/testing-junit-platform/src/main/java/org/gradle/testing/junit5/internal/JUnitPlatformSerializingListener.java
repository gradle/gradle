/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.testing.junit5.internal;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class JUnitPlatformSerializingListener implements TestExecutionListener {
    private final Map<String, TestDescriptorInternal> descriptorCache = new ConcurrentHashMap<>();
    private final ObjectOutputStream outputStream;

    public JUnitPlatformSerializingListener(ObjectOutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        System.out.println("Test plan started: " + testPlan.countTestIdentifiers(id -> true));
        // ignore for now
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.out.println("Test plan ended");
        // ignore for now
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
        System.out.println("Dynamic test: " + testIdentifier);
        // ignore for now
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        System.out.println("Test skipped: " + testIdentifier);
        TestDescriptorInternal test = getDescriptor(testIdentifier);
        sendEvent(new JUnitPlatformEvent(test, JUnitPlatformEvent.Type.SKIPPED, Instant.now().getEpochSecond(), null, null));
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        System.out.println("Test started: " + testIdentifier);
        TestDescriptorInternal test = getDescriptor(testIdentifier);
        sendEvent(new JUnitPlatformEvent(test, JUnitPlatformEvent.Type.START, Instant.now().getEpochSecond(), null, null));
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        System.out.println("Test finished: " + testIdentifier + " " + testExecutionResult.getStatus());
        TestDescriptorInternal test = getDescriptor(testIdentifier);
        JUnitPlatformEvent.Type type =  testExecutionResult.getStatus() == TestExecutionResult.Status.SUCCESSFUL ? JUnitPlatformEvent.Type.SUCCEEDED : JUnitPlatformEvent.Type.FAILED;
        sendEvent(new JUnitPlatformEvent(test, type, Instant.now().getEpochSecond(), null, testExecutionResult.getThrowable().orElse(null)));
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        System.out.println("Test reporting: " + testIdentifier + " " + entry);
        // ignore for now
    }

    private void sendEvent(JUnitPlatformEvent event) {
        try {
            outputStream.writeObject(event);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private TestDescriptorInternal getDescriptor(TestIdentifier test) {
        return descriptorCache.computeIfAbsent(test.getUniqueId(), id -> {
            TestDescriptorInternal parent = test.getParentId()
                .map(descriptorCache::get)
                .orElse(null);
            return new JUnitPlatformTestDescriptor(test, parent);
        });
    }
}
