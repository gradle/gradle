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

package org.gradle.internal.build.event;

import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.DefaultBuildOperationAncestryTracker;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

public class BuildEventServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(BuildEventListenerRegistryInternal.class, DefaultBuildEventsListenerRegistry.class);
        registration.addProvider(new ServiceRegistrationProvider() {
            @Provides
            BuildOperationAncestryTracker createBuildOperationAncestryTracker(BuildOperationListenerManager listenerManager) {
                DefaultBuildOperationAncestryTracker tracker = new DefaultBuildOperationAncestryTracker();
                listenerManager.addListener(tracker);
                return tracker;
            }
        });
    }
}
