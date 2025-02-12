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
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.internal.id.IdGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
class DefaultGroupTestEventReporter extends DefaultTestEventReporter implements GroupTestEventReporter {
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
    public TestEventReporter reportTest(String name, String displayName) {
        return new DefaultTestEventReporter(listener,
            new DecoratingTestDescriptor(new DefaultTestDescriptor(idGenerator.generateId(), testDescriptor.getClassName(), name, testDescriptor.getClassDisplayName(), displayName), testDescriptor),
            new TestResultState(testResultState)
        );
    }

    @Override
    public GroupTestEventReporter reportTestGroup(String name) {
        return new DefaultGroupTestEventReporter(
            listener,
            idGenerator,
            new DecoratingTestDescriptor(new DefaultTestClassDescriptor(idGenerator.generateId(), name), testDescriptor),
            new TestResultState(testResultState)
        );
    }
}
