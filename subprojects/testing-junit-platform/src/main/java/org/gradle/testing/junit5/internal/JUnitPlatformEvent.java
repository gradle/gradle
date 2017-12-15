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

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;

public interface JUnitPlatformEvent {
    class Started implements JUnitPlatformEvent {
        private final TestDescriptorInternal test;
        private final TestStartEvent event;

        public Started(TestDescriptorInternal test, TestStartEvent event) {
            this.test = test;
            this.event = event;
        }

        public TestDescriptorInternal getTest() {
            return test;
        }

        public TestStartEvent getEvent() {
            return event;
        }
    }

    class Completed implements JUnitPlatformEvent {
        private final TestDescriptorInternal test;
        private final TestCompleteEvent event;

        public Completed(TestDescriptorInternal test, TestCompleteEvent event) {
            this.test = test;
            this.event = event;
        }

        public TestDescriptorInternal getTest() {
            return test;
        }

        public TestCompleteEvent getEvent() {
            return event;
        }
    }

    class Output implements JUnitPlatformEvent  {
        private final TestDescriptorInternal test;
        private final TestOutputEvent event;

        public Output(TestDescriptorInternal test, TestOutputEvent event) {
            this.test = test;
            this.event = event;
        }

        public TestDescriptorInternal getTest() {
            return test;
        }

        public TestOutputEvent getEvent() {
            return event;
        }
    }

    class Failure implements JUnitPlatformEvent  {
        private final TestDescriptorInternal test;
        private final Throwable result;

        public Failure(TestDescriptorInternal test, Throwable result) {
            this.test = test;
            this.result = result;
        }

        public TestDescriptorInternal getTest() {
            return test;
        }

        public Throwable getResult() {
            return result;
        }
    }
}
