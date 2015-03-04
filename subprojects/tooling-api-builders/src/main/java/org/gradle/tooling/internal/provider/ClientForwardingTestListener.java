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
package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.tooling.internal.protocol.TestDescriptorVersion1;
import org.gradle.tooling.internal.protocol.TestProgressEventVersion1;
import org.gradle.tooling.internal.protocol.TestResultVersion1;

/**
 * Test listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 */
class ClientForwardingTestListener implements TestListener {

    private final BuildEventConsumer eventConsumer;

    ClientForwardingTestListener(BuildEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void beforeSuite(final TestDescriptor suite) {
        eventConsumer.dispatch(new TestProgressEventVersion1() {

            @Override
            public String getEventType() {
                return TestProgressEventVersion1.TEST_SUITE_STARTED;
            }

            @Override
            public TestDescriptorVersion1 getDescriptor() {
                return new TestDescriptorImpl(suite);
            }

            @Override
            public TestResultVersion1 getResult() {
                return null;
            }

        });
    }

    @Override
    public void afterSuite(final TestDescriptor suite, final TestResult result) {
        eventConsumer.dispatch(new TestProgressEventVersion1() {

            @Override
            public String getEventType() {
                TestResult.ResultType resultType = result.getResultType();
                switch (resultType) {
                    case SUCCESS:
                        return TestProgressEventVersion1.TEST_SUITE_SUCCEEDED;
                    case SKIPPED:
                        return TestProgressEventVersion1.TEST_SUITE_SKIPPED;
                    case FAILURE:
                        return TestProgressEventVersion1.TEST_SUITE_FAILED;
                    default:
                        throw new IllegalStateException("Unknown test result type: " + resultType);
                }
            }

            @Override
            public TestDescriptorVersion1 getDescriptor() {
                return new TestDescriptorImpl(suite);
            }

            @Override
            public TestResultVersion1 getResult() {
                return new TestResultImpl(result);
            }

        });
    }

    @Override
    public void beforeTest(final TestDescriptor test) {
        eventConsumer.dispatch(new TestProgressEventVersion1() {

            @Override
            public String getEventType() {
                return TestProgressEventVersion1.TEST_STARTED;
            }

            @Override
            public TestDescriptorVersion1 getDescriptor() {
                return new TestDescriptorImpl(test);
            }

            @Override
            public TestResultVersion1 getResult() {
                return null;
            }

        });
    }

    @Override
    public void afterTest(final TestDescriptor test, final TestResult result) {
        eventConsumer.dispatch(new TestProgressEventVersion1() {

            @Override
            public String getEventType() {
                TestResult.ResultType resultType = result.getResultType();
                switch (resultType) {
                    case SUCCESS:
                        return TestProgressEventVersion1.TEST_SUCCEEDED;
                    case SKIPPED:
                        return TestProgressEventVersion1.TEST_SKIPPED;
                    case FAILURE:
                        return TestProgressEventVersion1.TEST_FAILED;
                    default:
                        throw new IllegalStateException("Unknown test result type: " + resultType);
                }
            }

            @Override
            public TestDescriptorVersion1 getDescriptor() {
                return new TestDescriptorImpl(test);
            }

            @Override
            public TestResultVersion1 getResult() {
                return new TestResultImpl(result);
            }

        });
    }

    private static class TestDescriptorImpl implements TestDescriptorVersion1 {

        private final TestDescriptor testDescriptor;

        private TestDescriptorImpl(TestDescriptor testDescriptor) {
            this.testDescriptor = testDescriptor;
        }

        @Override
        public Object getId() {
            return ((TestDescriptorInternal) testDescriptor).getId();
        }

        @Override
        public String getName() {
            return testDescriptor.getName();
        }

        @Override
        public String getClassName() {
            return testDescriptor.getClassName();
        }

        @Override
        public TestDescriptorVersion1 getParent() {
            return null;
        }

    }

    private static class TestResultImpl implements TestResultVersion1 {

        private final TestResult result;

        private TestResultImpl(TestResult result) {
            this.result = result;
        }

        @Override
        public long getStartTime() {
            return result.getStartTime();
        }

        @Override
        public long getEndTime() {
            return result.getEndTime();
        }

    }

}
