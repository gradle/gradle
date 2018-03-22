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

package org.gradle.api.internal.tasks.testing;

import javax.annotation.Nullable;

public class DecoratingTestDescriptor implements TestDescriptorInternal {
    private final TestDescriptorInternal descriptor;
    private final TestDescriptorInternal parent;

    public DecoratingTestDescriptor(TestDescriptorInternal descriptor, TestDescriptorInternal parent) {
        this.descriptor = descriptor;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return descriptor.toString();
    }

    public TestDescriptorInternal getDescriptor() {
        return descriptor;
    }

    @Override
    public TestDescriptorInternal getParent() {
        return parent;
    }

    @Override
    public Object getId() {
        return descriptor.getId();
    }

    @Nullable
    @Override
    public Object getOwnerBuildOperationId() {
        return descriptor.getOwnerBuildOperationId();
    }

    @Override
    public String getDisplayName() {
        return descriptor.getDisplayName();
    }

    @Override
    public String getClassDisplayName() {
        return descriptor.getClassDisplayName();
    }

    @Override
    public String getClassName() {
        return descriptor.getClassName();
    }

    @Override
    public String getName() {
        return descriptor.getName();
    }

    @Override
    public boolean isComposite() {
        return descriptor.isComposite();
    }
}
