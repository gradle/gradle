/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.properties.ServiceReferenceSpec;
import org.gradle.api.tasks.TaskLifecycleListener;

/**
 * What is known about a valid {@link TaskLifecycleListener} type, regardless of the tasks it is registered with.
 */
public class TaskLifecycleListenerClassInfo {
    private final Class<?> acceptedTaskType;
    private final ImmutableSet<ServiceReferenceSpec> serviceReferences;

    public TaskLifecycleListenerClassInfo(Class<?> acceptedTaskType, ImmutableSet<ServiceReferenceSpec> serviceReferences) {
        this.acceptedTaskType = acceptedTaskType;
        this.serviceReferences = serviceReferences;
    }

    /**
     * The type of task the listener is declared for.
     */
    public Class<?> getAcceptedTaskType() {
        return acceptedTaskType;
    }

    /**
     * The build services the listener references via {@link org.gradle.api.services.ServiceReference}.
     */
    public ImmutableSet<ServiceReferenceSpec> getServiceReferences() {
        return serviceReferences;
    }
}
