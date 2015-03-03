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
import org.gradle.tooling.TestDescriptor;
import org.gradle.tooling.TestProgressEvent;
import org.gradle.tooling.TestProgressListener;
import org.gradle.tooling.TestResult;
import org.gradle.tooling.internal.protocol.TestDescriptorVersion1;
import org.gradle.tooling.internal.protocol.TestProgressEventVersion1;
import org.gradle.tooling.internal.protocol.TestProgressListenerVersion1;

class TestProgressListenerAdapter implements TestProgressListenerVersion1 {

    private final ListenerBroadcast<TestProgressListener> listeners = new ListenerBroadcast<TestProgressListener>(TestProgressListener.class);

    @Override
    public void onEvent(final TestProgressEventVersion1 event) {
        listeners.getSource().statusChanged(new TestProgressEvent() {
            @Override
            public int getEventTypeId() {
                return -1;
            }

            @Override
            public TestDescriptor getDescriptor() {
                return toTestDescriptor(event.getDescriptor());
            }

            @Override
            public TestResult getResult() {
                return null;
            }
        });
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

    public void add(TestProgressListener listener) {
        listeners.add(listener);
    }

}
