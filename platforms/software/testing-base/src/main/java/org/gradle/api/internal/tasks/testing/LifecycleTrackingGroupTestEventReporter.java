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

import java.util.HashSet;
import java.util.Set;

@NonNullApi
class LifecycleTrackingGroupTestEventReporter extends LifecycleTrackingTestEventReporter<GroupTestEventReporter> implements GroupTestEventReporter {
    private final Set<LifecycleTrackingTestEventReporter<?>> children = new HashSet<>();

    LifecycleTrackingGroupTestEventReporter(GroupTestEventReporter delegate) {
        super(delegate);
    }

    @Override
    protected void markCompleted() {
        for (LifecycleTrackingTestEventReporter<?> child : children) {
            if (!child.isCompleted()) {
                throw new IllegalStateException(String.format("Group was completed before tests (%s).", child));
            }
        }
        super.markCompleted();
    }

    @Override
    public void close() {
        super.close();
        // Now that it's safe, stop all children
        CompositeStoppable.stoppable(children).stop();
        children.clear();
    }

    @Override
    public TestEventReporter reportTest(String name, String displayName) {
        LifecycleTrackingTestEventReporter<TestEventReporter> child = new LifecycleTrackingTestEventReporter<>(delegate.reportTest(name, displayName));
        children.add(child);
        return child;
    }

    @Override
    public GroupTestEventReporter reportTestGroup(String name) {
        LifecycleTrackingGroupTestEventReporter child = new LifecycleTrackingGroupTestEventReporter(delegate.reportTestGroup(name));
        children.add(child);
        return child;
    }
}
