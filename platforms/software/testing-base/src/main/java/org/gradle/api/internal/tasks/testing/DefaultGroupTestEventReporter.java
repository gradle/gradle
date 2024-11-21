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

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.id.IdGenerator;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

@NonNullApi
public class DefaultGroupTestEventReporter extends DefaultTestEventReporter implements GroupTestEventReporter {
    private final IdGenerator<?> idGenerator;
    private Set<DefaultTestEventReporter> children = new HashSet<>();

    public DefaultGroupTestEventReporter(
        TestResultProcessor processor, IdGenerator<?> idGenerator, @Nullable DefaultGroupTestEventReporter parent, TestDescriptorInternal testDescriptor
    ) {
        super(processor, parent, testDescriptor);
        this.idGenerator = idGenerator;
    }

    void removeChild(DefaultTestEventReporter child) {
        if (children == null) {
            return;
        }
        children.remove(child);
    }

    @Override
    protected void cleanup() {
        // Prevent further removal calls from affecting the children set
        Set<DefaultTestEventReporter> childrenLocal = children;
        children = null;
        // Now that it's safe, stop all children
        CompositeStoppable.stoppable(childrenLocal).stop();
        super.cleanup();
    }

    @Override
    public TestEventReporter reportTest(String name, String displayName) {
        requireRunning();
        DefaultTestEventReporter child = new DefaultTestEventReporter(
            processor, this, new DefaultTestDescriptor(idGenerator.generateId(), null, name, null, displayName)
        );
        children.add(child);
        return child;
    }

    @Override
    public GroupTestEventReporter reportTestGroup(String name) {
        requireRunning();
        DefaultGroupTestEventReporter child = new DefaultGroupTestEventReporter(
            processor, idGenerator, this, new DefaultTestSuiteDescriptor(idGenerator.generateId(), name)
        );
        children.add(child);
        return child;
    }
}
