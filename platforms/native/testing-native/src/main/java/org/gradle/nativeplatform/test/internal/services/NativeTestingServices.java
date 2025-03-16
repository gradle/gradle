/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.nativeplatform.test.internal.services;

import org.gradle.api.reporting.components.internal.AbstractBinaryRenderer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.nativeplatform.test.internal.NativeTestSuiteBinaryRenderer;

public class NativeTestingServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(AbstractBinaryRenderer.class, NativeTestSuiteBinaryRenderer.class);
    }
}
