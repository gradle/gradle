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

package org.gradle.internal.encryption.services

import org.gradle.internal.encryption.EncryptionConfiguration
import org.gradle.internal.encryption.EncryptionService
import org.gradle.internal.encryption.impl.DefaultEncryptionService
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractGradleModuleServices


class EncryptionServices : AbstractGradleModuleServices() {
    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.add(EncryptionConfiguration::class.java, EncryptionService::class.java, DefaultEncryptionService::class.java)
    }
}
