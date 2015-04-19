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
import org.gradle.tooling.*;
import org.gradle.tooling.events.JvmTestKind;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.internal.DefaultFailureEvent;
import org.gradle.tooling.events.internal.DefaultSkippedEvent;
import org.gradle.tooling.events.internal.DefaultStartEvent;
import org.gradle.tooling.events.internal.DefaultSuccessEvent;
import org.gradle.tooling.internal.protocol.*;

import java.util.*;

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types (except the original logging-derived progress listener).
 */
class BuildProgressListenerAdapter implements BuildProgressListenerVersion1 {

    private final ListenerBroadcast<TestProgressListener> testProgressListeners = new ListenerBroadcast<TestProgressListener>(TestProgressListener.class);
    private final Map<Object, TestDescriptor> testDescriptorCache = new HashMap<Object, TestDescriptor>();

    BuildProgressListenerAdapter(List<TestProgressListener> testListeners) {
        this.testProgressListeners.addAll(testListeners);
    }

    @Override
    public List<String> getSubscribedEvents() {
        return this.testProgressListeners.isEmpty() ? Collections.<String>emptyList() : Collections.singletonList(BuildProgressListenerVersion1.TEST_PROGRESS);
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
        String testOutcome = event.getTestOutcome();
        final long eventTime = event.getEventTime();
        if (TestProgressEventVersion1.OUTCOME_STARTED.equals(testOutcome)) {
            TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), false);
            String eventDescription = toEventDescription(testDescriptor, "started");
            return new DefaultStartEvent(eventTime, eventDescription, testDescriptor);
        } else if (TestProgressEventVersion1.OUTCOME_FAILED.equals(testOutcome)) {
            TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            String eventDescription = toEventDescription(testDescriptor, "failed");
            TestFailure outcome = toTestFailure(event.getResult());
            return new DefaultFailureEvent(eventTime, eventDescription, testDescriptor, outcome);
        } else if (TestProgressEventVersion1.OUTCOME_SKIPPED.equals(testOutcome)) {
            TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            String eventDescription = toEventDescription(testDescriptor, "skipped");
            TestSuccess outcome = toTestSuccess(event.getResult());
            return new DefaultSkippedEvent(eventTime, eventDescription, testDescriptor, outcome);
        } else if (TestProgressEventVersion1.OUTCOME_SUCCEEDED.equals(testOutcome)) {
            TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            String eventDescription = toEventDescription(testDescriptor, "succeeded");
            TestSuccess outcome = toTestSuccess(event.getResult());
            return new DefaultSuccessEvent(eventTime, eventDescription, testDescriptor, outcome);
        } else {
            return null;
        }
    }

    private String toEventDescription(TestDescriptor testDescriptor, String progressLabel) {
        if (testDescriptor instanceof JvmTestDescriptor) {
            return String.format("%s '%s' %s.", ((JvmTestDescriptor) testDescriptor).getJvmTestKind().getLabel(), testDescriptor.getName(), progressLabel);
        } else {
            return String.format("Generic test '%s' %s.", testDescriptor.getName(), progressLabel);
        }
    }


    private TestDescriptor toTestDescriptor(final TestDescriptorVersion1 testDescriptor, boolean fromCache) {
        if (fromCache) {
            TestDescriptor cachedTestDescriptor = this.testDescriptorCache.get(testDescriptor.getId());
            if (cachedTestDescriptor == null) {
                throw new IllegalStateException(String.format("%s not available.", toString(testDescriptor)));
            } else {
                // when we access the test descriptor from the cache, it is because we have received a test finished event
                // once the test has finished, we can remove the test from the cache since no child will access it anymore
                // (all children have already finished before)
                this.testDescriptorCache.remove(testDescriptor.getId());
                return cachedTestDescriptor;
            }
        } else {
            TestDescriptor cachedTestDescriptor = this.testDescriptorCache.get(testDescriptor.getId());
            if (cachedTestDescriptor != null) {
                throw new IllegalStateException(String.format("%s already available.", toString(testDescriptor)));
            } else {
                final TestDescriptor parent = getParentTestDescriptor(testDescriptor);
                TestDescriptor newTestDescriptor = toTestDescriptor(testDescriptor, parent);
                testDescriptorCache.put(testDescriptor.getId(), newTestDescriptor);
                return newTestDescriptor;
            }
        }
    }

    private TestDescriptor toTestDescriptor(final TestDescriptorVersion1 testDescriptor, final TestDescriptor parent) {
        if (testDescriptor instanceof JvmTestDescriptorVersion1) {
            final JvmTestDescriptorVersion1 jvmTestDescriptor = (JvmTestDescriptorVersion1) testDescriptor;
            return new JvmTestDescriptor() {
                @Override
                public String getName() {
                    return jvmTestDescriptor.getName();
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
                public TestDescriptor getParent() {
                    return parent;
                }

            };
        } else {
            return new TestDescriptor() {
                @Override
                public String getName() {
                    return testDescriptor.getName();
                }

                @Override
                public TestDescriptor getParent() {
                    return parent;
                }
            };
        }
    }

    private JvmTestKind toJvmTestKind(JvmTestDescriptorVersion1 jvmTestDescriptor) {
        String jvmTestKind = jvmTestDescriptor.getTestKind();
        if (JvmTestDescriptorVersion1.KIND_SUITE.equals(jvmTestKind)) {
            return JvmTestKind.SUITE;
        } else if (JvmTestDescriptorVersion1.KIND_ATOMIC.equals(jvmTestKind)) {
            return JvmTestKind.ATOMIC;
        } else {
            return JvmTestKind.UNKNOWN;
        }
    }

    private TestSuccess toTestSuccess(final TestResultVersion1 testResult) {
        return new TestSuccess() {

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

    private TestFailure toTestFailure(final TestResultVersion1 testResult) {
        return new TestFailure() {

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
                List<FailureVersion1> origFailures = testResult.getFailures();
                List<Failure> failures = new ArrayList<Failure>(origFailures.size());
                for (FailureVersion1 origFailure : origFailures) {
                    failures.add(toFailure(origFailure));
                }
                return failures;
            }

        };
    }

    private static Failure toFailure(FailureVersion1 origFailure) {
        return origFailure == null ? null : new Failure(
                origFailure.getMessage(),
                origFailure.getDescription(),
                toFailure(origFailure.getCause()));
    }

    private TestDescriptor getParentTestDescriptor(TestDescriptorVersion1 testDescriptor) {
        Object parentId = testDescriptor.getParentId();
        if (parentId == null) {
            return null;
        } else {
            TestDescriptor parentTestDescriptor = testDescriptorCache.get(parentId);
            if (parentTestDescriptor == null) {
                throw new IllegalStateException(String.format("Parent test descriptor with id %s not available for %s.", parentId, toString(testDescriptor)));
            } else {
                return parentTestDescriptor;
            }
        }
    }

    private String toString(TestDescriptorVersion1 testDescriptor) {
        if (testDescriptor instanceof JvmTestDescriptorVersion1) {
            return String.format("TestDescriptor[id(%s), name(%s), className(%s), parent(%s)]",
                    testDescriptor.getId(), testDescriptor.getName(), ((JvmTestDescriptorVersion1) testDescriptor).getClassName(), testDescriptor.getParentId());
        } else {
            return String.format("TestDescriptor[id(%s), name(%s), parent(%s)]", testDescriptor.getId(), testDescriptor.getName(), testDescriptor.getParentId());
        }
    }

}
