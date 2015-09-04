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

package org.gradle.tooling.events.internal;

import org.gradle.api.Nullable;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;

/**
 * Implementation of the {@code BuildOperationDescriptor} interface.
 */
public class DefaultOperationDescriptor implements OperationDescriptor, OperationDescriptorWrapper {

    private final String name;
    private final String displayName;
    private final OperationDescriptor parent;
    private InternalOperationDescriptor internalDescriptor;

    public DefaultOperationDescriptor(InternalOperationDescriptor internalDescriptor,  OperationDescriptor parent) {
        this.name = internalDescriptor.getName();
        this.displayName = internalDescriptor.getDisplayName();
        this.parent = parent;
        this.internalDescriptor = internalDescriptor;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    @Override
    public OperationDescriptor getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public InternalOperationDescriptor getInternalOperationDescriptor() {
        return internalDescriptor;
    }
}
