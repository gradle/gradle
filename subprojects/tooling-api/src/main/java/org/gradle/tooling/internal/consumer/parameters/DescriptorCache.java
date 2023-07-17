/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.internal.Cast;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@NonNullApi
public class DescriptorCache {
    final Map<Object, OperationDescriptor> descriptorCache = new HashMap<>();

    public DescriptorCache() {}

    synchronized <T extends OperationDescriptor> T addDescriptor(InternalOperationDescriptor descriptor, T clientDescriptor) {
        if (this.descriptorCache.containsKey(descriptor.getId())) {
            throw new IllegalStateException(String.format("Operation %s already available.", descriptor));
        }
        descriptorCache.put(descriptor.getId(), clientDescriptor);
        return clientDescriptor;
    }

    synchronized <T extends OperationDescriptor> T removeDescriptor(Class<T> type, InternalOperationDescriptor descriptor) {
        OperationDescriptor cachedTestDescriptor = this.descriptorCache.remove(descriptor.getId());
        if (cachedTestDescriptor == null) {
            throw new IllegalStateException(String.format("Operation %s is not available.", descriptor));
        }
        return assertDescriptorType(type, cachedTestDescriptor);
    }

    <T extends OperationDescriptor> T assertDescriptorType(Class<T> type, OperationDescriptor descriptor) {
        Class<? extends OperationDescriptor> descriptorClass = descriptor.getClass();
        if (!type.isAssignableFrom(descriptorClass)) {
            throw new IllegalStateException(String.format("Unexpected operation type. Required %s but found %s", type.getName(), descriptorClass.getName()));
        }
        return Cast.uncheckedNonnullCast(descriptor);
    }

    OperationDescriptor toDescriptor(InternalOperationDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        return new DefaultOperationDescriptor(descriptor, parent);
    }

    synchronized OperationDescriptor getParentDescriptor(@Nullable Object parentId) {
        if (parentId == null) {
            return null;
        }
        OperationDescriptor operationDescriptor = descriptorCache.get(parentId);
        if (operationDescriptor == null) {
            throw new IllegalStateException(String.format("Parent operation with id %s not available.", parentId));
        }
        return operationDescriptor;
    }

    public Set<OperationDescriptor> collectDescriptors(Set<? extends InternalOperationDescriptor> dependencies) {
        Set<OperationDescriptor> result = new LinkedHashSet<OperationDescriptor>();
        for (InternalOperationDescriptor dependency : dependencies) {
            OperationDescriptor dependencyDescriptor = descriptorCache.get(dependency.getId());
            if (dependencyDescriptor != null) {
                result.add(dependencyDescriptor);
            }
        }
        return result;
    }

}
