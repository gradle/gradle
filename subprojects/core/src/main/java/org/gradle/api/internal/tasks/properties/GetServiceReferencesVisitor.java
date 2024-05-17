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

package org.gradle.api.internal.tasks.properties;

import com.google.common.collect.ImmutableSortedSet;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.services.BuildService;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;

import javax.annotation.Nullable;

public class GetServiceReferencesVisitor implements PropertyVisitor {
    private final ImmutableSortedSet.Builder<ServiceReferenceSpec> serviceReferences = ImmutableSortedSet.naturalOrder();

    public ImmutableSortedSet<ServiceReferenceSpec> getServiceReferences() {
        return serviceReferences.build();
    }

    @Override
    public void visitServiceReference(String propertyName, boolean optional, PropertyValue value, @Nullable String serviceName, Class<? extends BuildService<?>> buildServiceType) {
        serviceReferences.add(new DefaultServiceReferenceSpec(propertyName, buildServiceType, StringUtils.trimToEmpty(serviceName)));
    }
}
