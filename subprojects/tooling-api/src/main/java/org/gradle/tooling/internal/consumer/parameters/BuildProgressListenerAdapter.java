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
import org.gradle.tooling.internal.protocol.BuildProgressListenerVersion1;
import org.gradle.tooling.internal.protocol.TestDescriptorVersion1;
import org.gradle.tooling.internal.protocol.TestProgressEventVersion1;
import org.gradle.tooling.internal.protocol.TestResultVersion1;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        TestProgressEvent testProgressEvent = toTestProgressEvent(event);
        if (testProgressEvent != null) {
            testProgressListeners.getSource().statusChanged(testProgressEvent);
        }
    }

    private synchronized TestProgressEvent toTestProgressEvent(final TestProgressEventVersion1 event) {
        String testStructure = event.getTestStructure();
        String testOutcome = event.getTestOutcome();
        final long eventTme = event.getEventTime();
        if (TestProgressEventVersion1.STRUCTURE_SUITE.equals(testStructure)) {
            if (TestProgressEventVersion1.OUTCOME_STARTED.equals(testOutcome)) {
                final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), false);
                return new TestSuiteStartedEvent() {
                    @Override
                    public long getEventTime() {
                        return eventTme;
                    }

                    @Override
                    public String getDescription() {
                        return String.format("TestSuite '%s' started.", getTestDescriptor().getName());
                    }

                    @Override
                    public TestDescriptor getTestDescriptor() {
                        return testDescriptor;
                    }
                };
            } else if (TestProgressEventVersion1.OUTCOME_SKIPPED.equals(testOutcome)) {
                final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
                return new TestSuiteSkippedEvent() {
                    @Override
                    public long getEventTime() {
                        return eventTme;
                    }

                    @Override
                    public String getDescription() {
                        return String.format("TestSuite '%s' skipped.", getTestDescriptor().getName());
                    }

                    @Override
                    public TestDescriptor getTestDescriptor() {
                        return testDescriptor;
                    }
                };
            } else if (TestProgressEventVersion1.OUTCOME_SUCCEEDED.equals(testOutcome)) {
                final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
                final TestSuccess testSuccess = toTestSuccess(event.getResult());
                return new TestSuiteSucceededEvent() {
                    @Override
                    public long getEventTime() {
                        return eventTme;
                    }

                    @Override
                    public String getDescription() {
                        return String.format("TestSuite '%s' succeeded.", getTestDescriptor().getName());
                    }

                    @Override
                    public TestDescriptor getTestDescriptor() {
                        return testDescriptor;
                    }

                    @Override
                    public TestSuccess getTestResult() {
                        return testSuccess;
                    }
                };
            } else if (TestProgressEventVersion1.OUTCOME_FAILED.equals(testOutcome)) {
                final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
                final TestFailure testFailure = toTestFailure(event.getResult());
                return new TestSuiteFailedEvent() {
                    @Override
                    public long getEventTime() {
                        return eventTme;
                    }

                    @Override
                    public String getDescription() {
                        return String.format("TestSuite '%s' failed.", getTestDescriptor().getName());
                    }

                    @Override
                    public TestDescriptor getTestDescriptor() {
                        return testDescriptor;
                    }

                    @Override
                    public TestFailure getTestResult() {
                        return testFailure;
                    }
                };
            }
        } else if (TestProgressEventVersion1.STRUCTURE_ATOMIC.equals(testStructure)) {
            if (TestProgressEventVersion1.OUTCOME_STARTED.equals(testOutcome)) {
                final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), false);
                return new TestStartedEvent() {
                    @Override
                    public long getEventTime() {
                        return eventTme;
                    }

                    @Override
                    public String getDescription() {
                        return String.format("Test '%s' started.", getTestDescriptor().getName());
                    }

                    @Override
                    public TestDescriptor getTestDescriptor() {
                        return testDescriptor;
                    }
                };
            } else if (TestProgressEventVersion1.OUTCOME_SKIPPED.equals(testOutcome)) {
                final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
                return new TestSkippedEvent() {
                    @Override
                    public long getEventTime() {
                        return eventTme;
                    }

                    @Override
                    public String getDescription() {
                        return String.format("Test '%s' skipped.", getTestDescriptor().getName());
                    }

                    @Override
                    public TestDescriptor getTestDescriptor() {
                        return testDescriptor;
                    }
                };
            } else if (TestProgressEventVersion1.OUTCOME_SUCCEEDED.equals(testOutcome)) {
                final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
                final TestSuccess testSuccess = toTestSuccess(event.getResult());
                return new TestSucceededEvent() {
                    @Override
                    public long getEventTime() {
                        return eventTme;
                    }

                    @Override
                    public String getDescription() {
                        return String.format("Test '%s' succeeded.", getTestDescriptor().getName());
                    }

                    @Override
                    public TestDescriptor getTestDescriptor() {
                        return testDescriptor;
                    }

                    @Override
                    public TestSuccess getTestResult() {
                        return testSuccess;
                    }
                };
            } else if (TestProgressEventVersion1.OUTCOME_FAILED.equals(testOutcome)) {
                final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
                final TestFailure testFailure = toTestFailure(event.getResult());
                return new TestFailedEvent() {
                    @Override
                    public long getEventTime() {
                        return eventTme;
                    }

                    @Override
                    public String getDescription() {
                        return String.format("Test '%s' failed.", getTestDescriptor().getName());
                    }

                    @Override
                    public TestDescriptor getTestDescriptor() {
                        return testDescriptor;
                    }

                    @Override
                    public TestFailure getTestResult() {
                        return testFailure;
                    }
                };
            }
        }
        return null;
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
                TestDescriptor newTestDescriptor = new TestDescriptor() {

                    @Override
                    public String getName() {
                        return testDescriptor.getName();
                    }

                    @Override
                    public String getClassName() {
                        return testDescriptor.getClassName();
                    }

                    @Override
                    public TestDescriptor getParent() {
                        return parent;
                    }

                };
                testDescriptorCache.put(testDescriptor.getId(), newTestDescriptor);
                return newTestDescriptor;
            }
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
                return testResult.getFailures();
            }

        };
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
        return String.format("TestDescriptor[id(%s), name(%s), className(%s), parent(%s)]", testDescriptor.getId(), testDescriptor.getName(), testDescriptor.getClassName(), testDescriptor.getParentId());
    }

}
