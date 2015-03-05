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

import java.util.List;

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types.
 */
class BuildProgressListenerAdapter implements BuildProgressListenerVersion1 {

    private final ListenerBroadcast<TestProgressListener> testProgressListeners = new ListenerBroadcast<TestProgressListener>(TestProgressListener.class);

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
        final TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor());

        String eventType = event.getEventType();
        if (TestProgressEventVersion1.TEST_SUITE_STARTED.equals(eventType)) {
            return new TestSuiteStartedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SUITE_SKIPPED.equals(eventType)) {
            return new TestSuiteSkippedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SUITE_SUCCEEDED.equals(eventType)) {
            return new TestSuiteSucceededEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }

                @Override
                public TestSuccess getResult() {
                    return toTestSuccess(event.getResult());
                }
            };
        } else if (TestProgressEventVersion1.TEST_SUITE_FAILED.equals(eventType)) {
            return new TestSuiteFailedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }

                @Override
                public TestFailure getResult() {
                    return toTestFailure(event.getResult());
                }
            };
        } else if (TestProgressEventVersion1.TEST_STARTED.equals(eventType)) {
            return new TestStartedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SKIPPED.equals(eventType)) {
            return new TestSkippedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }
            };
        } else if (TestProgressEventVersion1.TEST_SUCCEEDED.equals(eventType)) {
            return new TestSucceededEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }

                @Override
                public TestSuccess getResult() {
                    return toTestSuccess(event.getResult());
                }
            };
        } else if (TestProgressEventVersion1.TEST_FAILED.equals(eventType)) {
            return new TestFailedEvent() {
                @Override
                public TestDescriptor getDescriptor() {
                    return testDescriptor;
                }

                @Override
                public TestFailure getResult() {
                    return toTestFailure(event.getResult());
                }
            };
        } else {
            return null;
        }
    }

    private TestDescriptor toTestDescriptor(final TestDescriptorVersion1 testDescriptor) {
        return new TestDescriptor() {

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
                TestDescriptorVersion1 parent = testDescriptor.getParent();
                return parent != null ? toTestDescriptor(testDescriptor) : null;
            }

        };
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

    public void addTestProgressListener(TestProgressListener listener) {
        testProgressListeners.add(listener);
    }

}
