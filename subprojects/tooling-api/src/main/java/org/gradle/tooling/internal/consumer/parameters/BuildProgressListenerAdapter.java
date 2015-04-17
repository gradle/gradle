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

import org.gradle.api.Nullable;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.tooling.Failure;
import org.gradle.tooling.JvmTestDescriptor;
import org.gradle.tooling.TestDescriptor;
import org.gradle.tooling.TestFailure;
import org.gradle.tooling.TestProgressListener;
import org.gradle.tooling.TestSuccess;
import org.gradle.tooling.events.FailureEvent;
import org.gradle.tooling.events.Income;
import org.gradle.tooling.events.Outcome;
import org.gradle.tooling.events.SkippedEvent;
import org.gradle.tooling.events.StartEvent;
import org.gradle.tooling.events.SuccessEvent;
import org.gradle.tooling.events.TestEvent;
import org.gradle.tooling.events.TestKind;
import org.gradle.tooling.events.TestProgressEvent;
import org.gradle.tooling.internal.protocol.BuildProgressListenerVersion1;
import org.gradle.tooling.internal.protocol.FailureVersion1;
import org.gradle.tooling.internal.protocol.JavaTestDescriptorVersion1;
import org.gradle.tooling.internal.protocol.TestDescriptorVersion1;
import org.gradle.tooling.internal.protocol.TestProgressEventVersion1;
import org.gradle.tooling.internal.protocol.TestResultVersion1;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        final TestKind testKind = TestProgressEventVersion1.STRUCTURE_SUITE.equals(testStructure) ? TestKind.suite :
                TestProgressEventVersion1.STRUCTURE_ATOMIC.equals(testStructure) ? TestKind.test : TestKind.unknown;
        if (testKind == TestKind.unknown || event.getDescriptor() == null) {
            return null;
        }
        String progressLabel = null;
        final List<Object> aggregate = new ArrayList<Object>();
        boolean isStart = false;
        if (TestProgressEventVersion1.OUTCOME_STARTED.equals(testOutcome)) {
            isStart = true;
            aggregate.add(new StartEvent() {
                @Override
                public Income getIncome() {
                    return null;
                }
            });
            progressLabel = "started";
        } else if (TestProgressEventVersion1.OUTCOME_FAILED.equals(testOutcome)) {
            aggregate.add(new FailureEvent() {
                @Override
                public Outcome getOutcome() {
                    return toTestFailure(event.getResult());
                }
            });
            progressLabel = "failed";
        } else if (TestProgressEventVersion1.OUTCOME_SKIPPED.equals(testOutcome)) {
            aggregate.add(new SkippedEvent() {
            });
            progressLabel = "skipped";
        } else if (TestProgressEventVersion1.OUTCOME_SUCCEEDED.equals(testOutcome)) {
            aggregate.add(new SuccessEvent() {
                @Override
                public Outcome getOutcome() {
                    return toTestSuccess(event.getResult());
                }
            });
            progressLabel = "succeeded";
        }
        TestDescriptor testDescriptor = toTestDescriptor(event.getDescriptor(), !isStart);
        String eventDescription = String.format("%s '%s' %s.", testKind.getLabel(), testDescriptor.getName(), progressLabel);
        TestEvent testEvent = new TestEvent(eventTme, eventDescription, testDescriptor, testKind);
        aggregate.add(testEvent);
        InvocationHandler handler = new DelegatingInvocationHandler(aggregate);
        Set<Class<?>> interfaces = collectInterfaces(aggregate);
        return (TestProgressEvent) Proxy.newProxyInstance(this.getClass().getClassLoader(), interfaces.toArray(new Class<?>[interfaces.size()]), handler);
    }

    private static Set<Class<?>> collectInterfaces(List<Object> aggregate) {
        Set<Class<?>> interfaces = new HashSet<Class<?>>(aggregate.size() + 1);
        interfaces.add(TestProgressEvent.class);
        for (Object payload : aggregate) {
            Class<?> payloadClass = payload.getClass();
            Collections.addAll(interfaces, payloadClass.getInterfaces());
        }
        return interfaces;
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
        if (testDescriptor instanceof JavaTestDescriptorVersion1) {
            return new JvmTestDescriptor() {

                @Override
                public String getClassName() {
                    return ((JavaTestDescriptorVersion1) testDescriptor).getClassName();
                }

                @Override
                public String getName() {
                    return testDescriptor.getName();
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

                @Nullable
                @Override
                public TestDescriptor getParent() {
                    return parent;
                }
            };
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
        if (origFailure == null) {
            return null;
        }
        return new Failure(
                origFailure.getMessage(),
                origFailure.getDescription(),
                toFailure(origFailure.getCause())
        );
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
        if (testDescriptor instanceof JavaTestDescriptorVersion1) {
            return String.format("TestDescriptor[id(%s), name(%s), className(%s), parent(%s)]",
                    testDescriptor.getId(), testDescriptor.getName(), ((JavaTestDescriptorVersion1) testDescriptor).getClassName(), testDescriptor.getParentId());
        } else {
            return String.format("TestDescriptor[id(%s), name(%s), parent(%s)]", testDescriptor.getId(), testDescriptor.getName(), testDescriptor.getParentId());
        }
    }

    private static class DelegatingInvocationHandler implements InvocationHandler {
        private final List<Object> aggregate;

        public DelegatingInvocationHandler(List<Object> aggregate) {
            this.aggregate = aggregate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            for (Object o : aggregate) {
                if (method.getDeclaringClass().isAssignableFrom(o.getClass())) {
                    return method.invoke(o, args);
                }
            }
            return null;
        }
    }
}
