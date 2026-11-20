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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.properties.ServiceReferenceSpec;
import org.gradle.api.tasks.TaskLifecycleListener;

/**
 * What is known about a valid {@link TaskLifecycleListener} type, regardless of the tasks it is registered with.
 */
public class TaskLifecycleListenerClassInfo {
    private final ImmutableList<Class<?>> acceptedTaskTypes;
    private final ImmutableSet<ServiceReferenceSpec> serviceReferences;

    public TaskLifecycleListenerClassInfo(ImmutableList<Class<?>> acceptedTaskTypes, ImmutableSet<ServiceReferenceSpec> serviceReferences) {
        this.acceptedTaskTypes = acceptedTaskTypes;
        this.serviceReferences = serviceReferences;
    }

    /**
     * The types that a task must have for the listener to accept it.
     * There are several when the listener is declared for a type variable with several bounds.
     */
    public ImmutableList<Class<?>> getAcceptedTaskTypes() {
        return acceptedTaskTypes;
    }

    /**
     * The build services the listener references via {@link org.gradle.api.services.ServiceReference}.
     */
    public ImmutableSet<ServiceReferenceSpec> getServiceReferences() {
        return serviceReferences;
    }
}
