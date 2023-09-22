/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.resources;

import com.google.common.collect.Maps;

import java.util.Map;

public class SharedResourceLeaseRegistry extends AbstractResourceLockRegistry<String, ResourceLock> {
    private final Map<String, LeaseHolder> sharedResources = Maps.newConcurrentMap();
    private final ResourceLockCoordinationService coordinationService;

    public SharedResourceLeaseRegistry(ResourceLockCoordinationService coordinationService) {
        super(coordinationService);
        this.coordinationService = coordinationService;
    }

    public void registerSharedResource(String name, int leases) {
        sharedResources.put(name, new LeaseHolder(leases));
    }

    public ResourceLock getResourceLock(final String sharedResource) {
        String displayName = "lease for " + sharedResource;
        return new DefaultLease(displayName, coordinationService, this, sharedResources.get(sharedResource));
    }
}
