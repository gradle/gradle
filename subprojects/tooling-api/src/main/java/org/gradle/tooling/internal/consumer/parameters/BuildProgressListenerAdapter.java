/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.tooling.Failure;
import org.gradle.tooling.events.FailureOutcome;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.SuccessOutcome;
import org.gradle.tooling.events.internal.DefaultFailureEvent;
import org.gradle.tooling.events.internal.DefaultSkippedEvent;
import org.gradle.tooling.events.internal.DefaultStartEvent;
import org.gradle.tooling.events.internal.DefaultSuccessEvent;
import org.gradle.tooling.events.test.JvmTestKind;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.TestProgressListener;
import org.gradle.tooling.internal.consumer.DefaultFailure;
import org.gradle.tooling.internal.protocol.*;

import java.util.*;

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types (except the original logging-derived progress listener).
 */
class BuildProgressListenerAdapter implements BuildProgressListenerVersion1 {

    private final ListenerBroadcast<TestProgressListener> testProgressListeners = new ListenerBroadcast<TestProgressListener>(TestProgressListener.class);
    private final Map<Object, TestOperationDescriptor> testDescriptorCache = new HashMap<Object, TestOperationDescriptor>();

    BuildProgressListenerAdapter(List<TestProgressListener> testListeners) {
        this.testProgressListeners.addAll(testListeners);
    }

    @Override
    public List<String> getSubscribedOperations() {
        return this.testProgressListeners.isEmpty() ? Collections.<String>emptyList() : Collections.singletonList(BuildProgressListenerVersion1.TEST_EXECUTION);
    }

    @Override
    public void onEvent(final Object event) {
        if (event instanceof TestProgressEventVersion1) {
            broadcastTestProgressEvent((TestProgressEventVersion1) event);
        }
    }

    private void broadcastTestProgressEvent(TestProgressEventVersion1 event) {
        ProgressEvent testProgressEvent = toTestProgressEvent(event);
        if (testProgressEvent != null) {
            testProgressListeners.getSource().statusChanged(testProgressEvent);
        }
    }

    private synchronized ProgressEvent toTestProgressEvent(final TestProgressEventVersion1 event) {
        if (event instanceof TestStartedProgressEventVersion1) {
            return startedEvent((TestStartedProgressEventVersion1) event);
        } else if (event instanceof TestFinishedProgressEventVersion1) {
            return finishedEvent((TestFinishedProgressEventVersion1) event);
        }
        return null;
    }

    private ProgressEvent startedEvent(TestStartedProgressEventVersion1 event) {
        TestOperationDescriptor testDescriptor = addTestDescriptor(event.getDescriptor());
        String eventDescription = event.getDisplayName();
        final long eventTime = event.getEventTime();
        return new DefaultStartEvent(eventTime, eventDescription, testDescriptor);
    }

    private ProgressEvent finishedEvent(TestFinishedProgressEventVersion1 event) {
        TestOperationDescriptor testDescriptor = removeTestDescriptor(event.getDescriptor());
        String eventDescription = event.getDisplayName();
        final long eventTime = event.getEventTime();
        if (event.getResult().getResultType().equals(TestResultVersion1.RESULT_FAILED)) {
            FailureOutcome outcome = toTestFailure(event.getResult());
            return new DefaultFailureEvent(eventTime, eventDescription, testDescriptor, outcome);
        } else if (event.getResult().getResultType().equals(TestResultVersion1.RESULT_SKIPPED)) {
            SuccessOutcome outcome = toTestSuccess(event.getResult());
            return new DefaultSkippedEvent(eventTime, eventDescription, testDescriptor, outcome);
        } else if (event.getResult().getResultType().equals(TestResultVersion1.RESULT_SUCCESSFUL)) {
            SuccessOutcome outcome = toTestSuccess(event.getResult());
            return new DefaultSuccessEvent(eventTime, eventDescription, testDescriptor, outcome);
        }
        throw new IllegalArgumentException("Cannot adapt progress event: " + event);
    }

    private TestOperationDescriptor removeTestDescriptor(TestDescriptorVersion1 testDescriptor) {
        TestOperationDescriptor cachedTestDescriptor = this.testDescriptorCache.remove(testDescriptor.getId());
        if (cachedTestDescriptor == null) {
            throw new IllegalStateException(String.format("Operation %s is not available.", toString(testDescriptor)));
        }
        return cachedTestDescriptor;
    }

    private TestOperationDescriptor addTestDescriptor(TestDescriptorVersion1 testDescriptor) {
        TestOperationDescriptor cachedTestDescriptor = this.testDescriptorCache.get(testDescriptor.getId());
        if (cachedTestDescriptor != null) {
            throw new IllegalStateException(String.format("Operation %s already available.", toString(testDescriptor)));
        }
        final TestOperationDescriptor parent = getParentTestDescriptor(testDescriptor);
        TestOperationDescriptor newTestDescriptor = toTestDescriptor(testDescriptor, parent);
        testDescriptorCache.put(testDescriptor.getId(), newTestDescriptor);
        return newTestDescriptor;
    }

    private TestOperationDescriptor toTestDescriptor(final TestDescriptorVersion1 testDescriptor, final TestOperationDescriptor parent) {
        if (testDescriptor instanceof JvmTestDescriptorVersion1) {
            final JvmTestDescriptorVersion1 jvmTestDescriptor = (JvmTestDescriptorVersion1) testDescriptor;
            return new JvmTestOperationDescriptor() {
                @Override
                public String getName() {
                    return jvmTestDescriptor.getName();
                }

                @Override
                public String toString() {
                    return getDisplayName();
                }

                @Override
                public String getDisplayName() {
                    return jvmTestDescriptor.getDisplayName();
                }

                @Override
                public JvmTestKind getJvmTestKind() {
                    return toJvmTestKind(jvmTestDescriptor);
                }

                @Override
                public String getSuiteName() {
                    return jvmTestDescriptor.getSuiteName();
                }

                @Override
                public String getClassName() {
                    return jvmTestDescriptor.getClassName();
                }

                @Override
                public String getMethodName() {
                    return jvmTestDescriptor.getMethodName();
                }

                @Override
                public TestOperationDescriptor getParent() {
                    return parent;
                }

            };
        } else {
            return new TestOperationDescriptor() {
                @Override
                public String getName() {
                    return testDescriptor.getName();
                }

                @Override
                public String toString() {
                    return getDisplayName();
                }

                @Override
                public String getDisplayName() {
                    return testDescriptor.getDisplayName();
                }

                @Override
                public TestOperationDescriptor getParent() {
                    return parent;
                }
            };
        }
    }

    private static JvmTestKind toJvmTestKind(JvmTestDescriptorVersion1 jvmTestDescriptor) {
        String jvmTestKind = jvmTestDescriptor.getTestKind();
        if (JvmTestDescriptorVersion1.KIND_SUITE.equals(jvmTestKind)) {
            return JvmTestKind.SUITE;
        } else if (JvmTestDescriptorVersion1.KIND_ATOMIC.equals(jvmTestKind)) {
            return JvmTestKind.ATOMIC;
        } else {
            return JvmTestKind.UNKNOWN;
        }
    }

    private SuccessOutcome toTestSuccess(final TestResultVersion1 testResult) {
        return new SuccessOutcome() {

            @Override
            public long getStartTime() {
                return testResult.getStartTime();
            }

            @Override
            public long getEndTime() {
                return testResult.getEndTime();
            }

        };
    }

    private FailureOutcome toTestFailure(final TestResultVersion1 testResult) {
        return new FailureOutcome() {

            @Override
            public long getStartTime() {
                return testResult.getStartTime();
            }

            @Override
            public long getEndTime() {
                return testResult.getEndTime();
            }

            @Override
            public List<Failure> getFailures() {
                List<? extends FailureVersion1> origFailures = testResult.getFailures();
                List<Failure> failures = new ArrayList<Failure>(origFailures.size());
                for (FailureVersion1 origFailure : origFailures) {
                    failures.add(toFailure(origFailure));
                }
                return failures;
            }

        };
    }

    private static Failure toFailure(FailureVersion1 origFailure) {
        return origFailure == null ? null : new DefaultFailure(
                origFailure.getMessage(),
                origFailure.getDescription(),
                toFailure(origFailure.getCauses()));
    }

    private static List<Failure> toFailure(List<? extends FailureVersion1> causes) {
        List<Failure> result = new ArrayList<Failure>();
        for (FailureVersion1 cause : causes) {
            result.add(toFailure(cause));
        }
        return result;
    }

    private TestOperationDescriptor getParentTestDescriptor(TestDescriptorVersion1 testDescriptor) {
        Object parentId = testDescriptor.getParentId();
        if (parentId == null) {
            return null;
        } else {
            TestOperationDescriptor parentTestDescriptor = testDescriptorCache.get(parentId);
            if (parentTestDescriptor == null) {
                throw new IllegalStateException(String.format("Parent test descriptor with id %s not available for %s.", parentId, toString(testDescriptor)));
            } else {
                return parentTestDescriptor;
            }
        }
    }

    private String toString(TestDescriptorVersion1 testDescriptor) {
        if (testDescriptor instanceof JvmTestDescriptorVersion1) {
            return String.format("TestOperationDescriptor[id(%s), name(%s), className(%s), parent(%s)]",
                    testDescriptor.getId(), testDescriptor.getName(), ((JvmTestDescriptorVersion1) testDescriptor).getClassName(), testDescriptor.getParentId());
        } else {
            return String.format("TestOperationDescriptor[id(%s), name(%s), parent(%s)]", testDescriptor.getId(), testDescriptor.getName(), testDescriptor.getParentId());
        }
    }

}
