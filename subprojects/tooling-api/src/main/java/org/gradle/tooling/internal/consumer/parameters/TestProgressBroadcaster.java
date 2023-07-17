/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.JvmTestKind;
import org.gradle.tooling.events.test.TestFinishEvent;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationResult;
import org.gradle.tooling.events.test.TestProgressEvent;
import org.gradle.tooling.events.test.TestStartEvent;
import org.gradle.tooling.events.test.internal.DefaultJvmTestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestFailureResult;
import org.gradle.tooling.events.test.internal.DefaultTestFinishEvent;
import org.gradle.tooling.events.test.internal.DefaultTestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestSkippedResult;
import org.gradle.tooling.events.test.internal.DefaultTestStartEvent;
import org.gradle.tooling.events.test.internal.DefaultTestSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTestFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestResult;
import org.gradle.tooling.internal.protocol.events.InternalTestSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTestStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestSuccessResult;

@NonNullApi
public class TestProgressBroadcaster extends ProgressListenerBroadcasterAdapter implements ProgressListenerBroadcaster {
    private final DescriptorCache dc;

    public TestProgressBroadcaster(DescriptorCache descriptorCache) {
        super(InternalTestProgressEvent.class, TestProgressEvent.class, null);
        this.dc = descriptorCache;
    }

    @Override
    public void broadCast(Object progressEvent) {
        TestProgressEvent testProgressEvent = toTestProgressEvent((InternalTestProgressEvent) progressEvent);
        if (testProgressEvent != null) {
            getListeners().getSource().statusChanged(testProgressEvent);
        }
    }

    private static JvmTestKind toJvmTestKind(String testKind) {
        if (InternalJvmTestDescriptor.KIND_SUITE.equals(testKind)) {
            return JvmTestKind.SUITE;
        } else if (InternalJvmTestDescriptor.KIND_ATOMIC.equals(testKind)) {
            return JvmTestKind.ATOMIC;
        } else {
            return JvmTestKind.UNKNOWN;
        }
    }

    private TestProgressEvent toTestProgressEvent(InternalTestProgressEvent event) {
        if (event instanceof InternalTestStartedProgressEvent) {
            return testStartedEvent((InternalTestStartedProgressEvent) event);
        } else if (event instanceof InternalTestFinishedProgressEvent) {
            return testFinishedEvent((InternalTestFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private TestFinishEvent testFinishedEvent(InternalTestFinishedProgressEvent event) {
        TestOperationDescriptor clientDescriptor = dc.removeDescriptor(TestOperationDescriptor.class, event.getDescriptor());
        return new DefaultTestFinishEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor, toTestResult(event.getResult()));
    }

    private static TestOperationResult toTestResult(InternalTestResult result) {
        if (result instanceof InternalTestSuccessResult) {
            return new DefaultTestSuccessResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalTestSkippedResult) {
            return new DefaultTestSkippedResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalTestFailureResult) {
            return new DefaultTestFailureResult(result.getStartTime(), result.getEndTime(), BuildProgressListenerAdapter.toFailures(result.getFailures()));
        } else {
            return null;
        }
    }

    private TestStartEvent testStartedEvent(InternalTestStartedProgressEvent event) {
        TestOperationDescriptor clientDescriptor = dc.addDescriptor(event.getDescriptor(), toTestDescriptor(event.getDescriptor()));
        return new DefaultTestStartEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor);
    }

    private TestOperationDescriptor toTestDescriptor(InternalTestDescriptor descriptor) {
        OperationDescriptor parent = dc.getParentDescriptor(descriptor.getParentId());
        if (descriptor instanceof InternalJvmTestDescriptor) {
            InternalJvmTestDescriptor jvmTestDescriptor = (InternalJvmTestDescriptor) descriptor;
            return new DefaultJvmTestOperationDescriptor(jvmTestDescriptor, parent,
                toJvmTestKind(jvmTestDescriptor.getTestKind()), jvmTestDescriptor.getSuiteName(), jvmTestDescriptor.getClassName(), jvmTestDescriptor.getMethodName());
        }
        return new DefaultTestOperationDescriptor(descriptor, parent);
    }

}
