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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types.
 */
class BuildProgressListenerAdapter implements BuildProgressListenerVersion1 {

    private final ListenerBroadcast<TestProgressListener> testProgressListeners = new ListenerBroadcast<TestProgressListener>(TestProgressListener.class);

    private final Map<Object, TestDescriptor> testDescriptorCache = new HashMap<Object, TestDescriptor>();

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

    private TestProgressEvent toTestProgressEvent(final TestProgressEventVersion1 event) {
        String eventType = event.getEventType();
        if (TestProgressEventVersion1.TEST_SUITE_STARTED.equals(eventType)) {
            final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), false);
            return new TestSuiteStartedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SUITE_SKIPPED.equals(eventType)) {
            final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            return new TestSuiteSkippedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SUITE_SUCCEEDED.equals(eventType)) {
            final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            final TestSuccess testSuccess = toTestSuccess(event.getResult());
            return new TestSuiteSucceededEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }

                @Override
                public TestSuccess getResult() {
                    return testSuccess;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SUITE_FAILED.equals(eventType)) {
            final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            final TestFailure testFailure = toTestFailure(event.getResult());
            return new TestSuiteFailedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }

                @Override
                public TestFailure getResult() {
                    return testFailure;
                }
            };
        } else if (TestProgressEventVersion1.TEST_STARTED.equals(eventType)) {
            final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), false);
            return new TestStartedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SKIPPED.equals(eventType)) {
            final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            return new TestSkippedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SUCCEEDED.equals(eventType)) {
            final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            final TestSuccess testSuccess = toTestSuccess(event.getResult());
            return new TestSucceededEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }

                @Override
                public TestSuccess getResult() {
                    return testSuccess;
                }
            };
        } else if (TestProgressEventVersion1.TEST_FAILED.equals(eventType)) {
            final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), true);
            final TestFailure testFailure = toTestFailure(event.getResult());
            return new TestFailedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }

                @Override
                public TestFailure getResult() {
                    return testFailure;
                }
            };
        } else {
            return null;
        }
    }

    private TestDescriptor toTestDescriptor(final TestDescriptorVersion1 testDescriptor, boolean fromCache) {
        if (fromCache) {
            TestDescriptor cachedTestDescriptor = this.testDescriptorCache.get(testDescriptor.getId());
            if (cachedTestDescriptor == null) {
                throw new IllegalStateException(String.format("Test descriptor %s not available.", testDescriptor.getId()));
            } else {
                return cachedTestDescriptor;
            }
        } else {
            TestDescriptor cachedTestDescriptor = this.testDescriptorCache.get(testDescriptor.getId());
            if (cachedTestDescriptor != null) {
                throw new IllegalStateException(String.format("Test descriptor %s already available.", testDescriptor.getId()));
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
            public List<Throwable> getExceptions() {
                return testResult.getExceptions();
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
                throw new IllegalStateException(String.format("Parent test descriptor %s not available for test descriptor %s.", parentId, testDescriptor.getId()));
            } else {
                return parentTestDescriptor;
            }
        }
    }

    public void addTestProgressListener(TestProgressListener listener) {
        testProgressListeners.add(listener);
    }

}
