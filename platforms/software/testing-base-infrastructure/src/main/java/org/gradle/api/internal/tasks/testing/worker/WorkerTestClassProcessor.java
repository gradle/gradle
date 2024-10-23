/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.worker;

import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.SuiteTestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.internal.time.Clock;

public class WorkerTestClassProcessor extends SuiteTestClassProcessor {

    public WorkerTestClassProcessor(TestClassProcessor processor, Object workerSuiteId, String workerDisplayName,
                                    Clock clock) {
        super(new WorkerTestSuiteDescriptor(workerSuiteId, workerDisplayName), processor, clock);
    }

    public static class WorkerTestSuiteDescriptor extends DefaultTestSuiteDescriptor {
        public WorkerTestSuiteDescriptor(Object id, String name) {
            super(id, name);
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
