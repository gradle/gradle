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

package org.gradle.internal.extensions.core

import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistration.Contracts.provides


/**
 * @param ImplementationType The implementation type of the service.
 * @param ServiceType The service to make visible.
 * @see [ServiceRegistration.add]
 */
inline fun <reified ImplementationType, reified ServiceType> ServiceRegistration.add() where ImplementationType : ServiceType {
    add(ImplementationType::class.java, provides(ServiceType::class.java))
}


/**
 * @param ServiceType The service to make visible.
 * @see [ServiceRegistration.add]
 */
inline fun <reified ServiceType : Any> ServiceRegistration.add(instance: ServiceType) {
    add(ServiceType::class.java, instance)
}
