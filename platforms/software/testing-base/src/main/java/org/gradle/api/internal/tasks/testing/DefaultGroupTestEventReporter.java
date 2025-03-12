/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.internal.id.IdGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
class DefaultGroupTestEventReporter extends DefaultTestEventReporter implements GroupTestEventReporterInternal {
    private final IdGenerator<?> idGenerator;

    DefaultGroupTestEventReporter(TestListenerInternal listener, IdGenerator<?> idGenerator, TestDescriptorInternal testDescriptor, TestResultState testResultState) {
        super(listener, testDescriptor, testResultState);
        this.idGenerator = idGenerator;
    }

    @Override
    protected boolean isComposite() {
        return true;
    }

    @Override
    public DefaultTestEventReporter reportTest(String name, String displayName) {
        return reportTestDirectly(new DefaultTestDescriptor(idGenerator.generateId(), testDescriptor.getClassName(), name, testDescriptor.getClassDisplayName(), displayName));
    }

    @Override
    public DefaultTestEventReporter reportTestDirectly(TestDescriptorInternal testDescriptor) {
        if (testDescriptor.getParent() != this.testDescriptor) {
            throw new IllegalArgumentException("Test descriptor " + testDescriptor + " must have this as a parent: " + this.testDescriptor);
        }
        return new DefaultTestEventReporter(
            listener,
            testDescriptor,
            new TestResultState(testResultState)
        );
    }

    @Override
    public DefaultGroupTestEventReporter reportTestGroup(String name) {
        return reportTestGroupDirectly(new DefaultTestClassDescriptor(idGenerator.generateId(), name));
    }

    @Override
    public DefaultGroupTestEventReporter reportTestGroupDirectly(TestDescriptorInternal testDescriptor) {
        if (testDescriptor.getParent() != this.testDescriptor) {
            throw new IllegalArgumentException("Test descriptor " + testDescriptor + " must have this as a parent: " + this.testDescriptor);
        }
        return new DefaultGroupTestEventReporter(
            listener,
            idGenerator,
            testDescriptor,
            new TestResultState(testResultState)
        );
    }
}
